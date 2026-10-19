package top.imsyy.splayer.android.playback;

import static org.junit.Assert.*;
import android.net.Uri;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import top.imsyy.splayer.android.cache.AudioCacheProvider;

/** Real HTTP bytes through the production DataSource; test-owned unique names only. */
public class NativeAudioCacheIdentityTest {
  private static byte[] wave(short sample) {
    ByteBuffer data=ByteBuffer.allocate(1068).order(ByteOrder.LITTLE_ENDIAN);
    data.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(1060);
    data.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short)1)
        .putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16);
    data.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(1024);
    for(int i=0;i<512;i++)data.putShort(sample);
    return data.array();
  }

  private static final class AudioServer implements AutoCloseable {
    final byte[] first,second;
    final ServerSocket server=new ServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
    final AtomicInteger requests=new AtomicInteger();
    final AtomicReference<Exception> failure=new AtomicReference<>();
    final Thread worker;
    AudioServer() throws Exception {this((short)1111,(short)2222);}
    AudioServer(short firstSample,short secondSample) throws Exception {
      first=wave(firstSample);second=wave(secondSample);
      worker=new Thread(()->{
        while(!server.isClosed())try(Socket client=server.accept()) {
          client.setSoTimeout(3000);
          BufferedReader input=new BufferedReader(new InputStreamReader(client.getInputStream(),StandardCharsets.US_ASCII));
          String request=input.readLine(),line;
          while((line=input.readLine())!=null&&!line.isEmpty()){}
          byte[] body=request!=null&&(request.contains("id=2")||request.contains("quality-high")||request.contains("token=second"))?second:first;
          requests.incrementAndGet();
          var output=client.getOutputStream();
          output.write(("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
          output.write(body);output.flush();
        } catch(Exception error) {if(!server.isClosed())failure.compareAndSet(null,error);}
      },"test-cache-audio-http");
      worker.setDaemon(true);worker.start();
    }
    String url(String name,int id){return "http://127.0.0.1:"+server.getLocalPort()+"/"+name+"?id="+id;}
    @Override public void close() throws Exception {
      server.close();worker.join(3500);
      if(failure.get()!=null)throw new java.io.IOException("Test audio server failed",failure.get());
    }
  }

  private byte[] read(String url) throws Exception {
    return read(url,null);
  }
  private byte[] read(String url,String key) throws Exception {
    var context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    assertTrue("Only the isolated test app",context.getPackageName().endsWith(".debug"));
    DataSource source=AudioCacheProvider.buildCachedDataSourceFactory(context).createDataSource();
    try {
      source.open(new DataSpec.Builder().setUri(Uri.parse(url)).setKey(key).build());
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
      while((count=source.read(buffer,0,buffer.length))!=-1)bytes.write(buffer,0,count);
      return bytes.toByteArray();
    } finally {source.close();}
  }

  @Test public void differentSongQueriesDoNotReuseCachedAudioBytes() throws Exception {
    try(AudioServer server=new AudioServer()) {
      String name="cache-proof-"+UUID.randomUUID()+".wav";
      assertArrayEquals(server.first,read(server.url(name,1)));
      assertArrayEquals("A different song-id query must read the second resource",server.second,read(server.url(name,2)));
    }
  }

  @Test public void sameFilenameOnDifferentServersDoesNotReuseBytes() throws Exception {
    try(AudioServer first=new AudioServer();AudioServer second=new AudioServer((short)3333,(short)4444)) {
      String name="cache-proof-"+UUID.randomUUID()+".wav";
      assertArrayEquals(first.first,read(first.url(name,1)));
      assertArrayEquals(second.first,read(second.url(name,1)));
    }
  }

  @Test public void qualityPathsAndOpaqueTokensKeepDistinctBytes() throws Exception {
    try(AudioServer server=new AudioServer()) {
      String name="cache-proof-"+UUID.randomUUID()+".wav";
      assertArrayEquals(server.first,read(server.url("quality-low/"+name,1)));
      assertArrayEquals(server.second,read(server.url("quality-high/"+name,1)));
      name="cache-proof-"+UUID.randomUUID()+".wav";
      assertArrayEquals(server.first,read(server.url(name,1)+"&token=first"));
      assertArrayEquals(server.second,read(server.url(name,1)+"&token=second"));
    }
  }

  @Test public void explicitStableIdentityReusesTheSameAudioAfterUrlRotation() throws Exception {
    try(AudioServer server=new AudioServer()) {
      String key="test-stable-resource:"+UUID.randomUUID();
      assertArrayEquals(server.first,read(server.url("cache-proof-"+UUID.randomUUID()+".wav",1)+"&signature=first",key));
      assertArrayEquals(server.first,read(server.url("cache-proof-"+UUID.randomUUID()+".wav",1)+"&signature=second",key));
      assertEquals("Known identical media should hit cache after URL rotation",1,server.requests.get());
    }
  }

  @Test public void legacyBytesRemainIntactButAreNotGuessedToBelongToAnotherSong() throws Exception {
    try(AudioServer server=new AudioServer()) {
      String name="cache-proof-"+UUID.randomUUID()+".wav",legacy="aud:"+name;
      assertArrayEquals(server.first,read(server.url(name,1),legacy));
      assertArrayEquals(server.second,read(server.url(name,2)));
      String oldUrl=server.url(name,1);server.close();
      assertArrayEquals("Old owned bytes must still exist without network",server.first,read(oldUrl,legacy));
    }
  }

  @Test public void trustedContentIdentityKeepsBusinessQueriesAndPublisherBoundaries() {
    String file="0123456789abcdef0123456789abcdef.mp3";
    String first=AudioCacheProvider.resolveCacheKey(Uri.parse("https://m701.music.126.net/one/"+file+"?token=first&expires=1"));
    assertEquals(first,AudioCacheProvider.resolveCacheKey(Uri.parse("https://m801.music.126.net/two/"+file+"?token=second&expires=2")));
    assertNotEquals(first,AudioCacheProvider.resolveCacheKey(Uri.parse("https://other.example/"+file)));
    assertNotEquals(first,AudioCacheProvider.resolveCacheKey(Uri.parse("https://m701.music.126.net/"+file+"?id=2")));
    assertNotEquals(AudioCacheProvider.resolveCacheKey(Uri.parse("https://m701.music.126.net/"+file+"?a=1%26b%3D2")),
        AudioCacheProvider.resolveCacheKey(Uri.parse("https://m701.music.126.net/"+file+"?a=1&b=2")));
  }
}
