package top.imsyy.splayer.android.download;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;

/** Real isolated documents and HTTP, including persisted state across transfer instances. */
public class DownloadFileSafetyTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void replacedSameUriSurvivesRecoveryAndNetworkFailure() throws Exception {
    DownloadTransferTest.Filesystem storage = new DownloadTransferTest.Filesystem(temporary.newFolder());
    File song = new File(storage.directory, "song.mp3");
    byte[] repaired = {9, 8, 7, 6};
    Files.write(song.toPath(), repaired);
    Files.write(new File(storage.directory, DownloadTransfer.pendingName("song.mp3")).toPath(),
        song.getAbsolutePath().getBytes("UTF-8"));
    try {
      DownloadTransfer.download("retry", "http://127.0.0.1:1/offline", "song.mp3", storage,
          new DownloadTransfer.Control(), (read, total) -> {});
      fail("offline retry must fail");
    } catch (IOException expected) {}
    assertTrue("recovery must never delete a public document", song.exists());
    assertArrayEquals(repaired, Files.readAllBytes(song.toPath()));
  }

  @Test public void createdDocumentIsProtectedWhenNameQueryAndDeleteFail() throws Exception {
    DownloadTransferTest.Filesystem storage = new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public String name(String path) {
        if (path.endsWith("song.mp3")) throw new IllegalStateException("provider metadata unavailable");
        return super.name(path);
      }
      public boolean delete(String path) {
        if (path.endsWith("song.mp3")) throw new IllegalStateException("provider delete unavailable");
        return super.delete(path);
      }
    };
    try (DownloadTransferTest.Server server = new DownloadTransferTest.Server("Content-Length: 4\r\n", new byte[]{1,2,3,4})) {
      try {
        DownloadTransfer.download("metadata", server.url(), "song.mp3", storage,
            new DownloadTransfer.Control(), (read, total) -> {});
        fail("metadata failure cannot publish");
      } catch (IOException | RuntimeException expected) {}
    }
    assertNotNull("created audio remains present", storage.find("song.mp3"));
    assertNotNull("unfinished protection must survive cleanup", storage.find(DownloadTransfer.pendingName("song.mp3")));
  }

  private DownloadTransferTest.Filesystem marked(byte[] bytes, String marker) throws Exception {
    DownloadTransferTest.Filesystem s = new DownloadTransferTest.Filesystem(temporary.newFolder());
    if (bytes != null) Files.write(new File(s.directory,"song.mp3").toPath(),bytes);
    Files.write(new File(s.directory,DownloadTransfer.pendingName("song.mp3")).toPath(),
        (marker == null ? new File(s.directory,"song.mp3").getAbsolutePath() : marker).getBytes("UTF-8"));
    return s;
  }
  private void preserved(DownloadTransferTest.Filesystem s, byte[] bytes, DownloadTransfer.Control c) throws Exception {
    try (DownloadTransferTest.Server server = new DownloadTransferTest.Server("Content-Length: 4\r\n", new byte[]{1,2,3,4})) {
      try { DownloadTransfer.download("safety",server.url(),"song.mp3",s,c,(read,total)->{}); }
      catch (IOException expected) {}
    }
    assertArrayEquals(bytes,Files.readAllBytes(new File(s.directory,"song.mp3").toPath()));
  }
  @Test public void repairedSameUriIsVerifiedWithoutRewriting() throws Exception {
    byte[] bytes={1,2,3,4}; DownloadTransferTest.Filesystem s=marked(bytes,null);
    try (DownloadTransferTest.Server server=new DownloadTransferTest.Server("Content-Length: 4\r\n",bytes)) {
      assertEquals("skipped",DownloadTransfer.download("repair",server.url(),"song.mp3",s,
          new DownloadTransfer.Control(),(read,total)->{}).status);
    }
    assertArrayEquals(bytes,Files.readAllBytes(new File(s.find("song.mp3")).toPath()));
    assertNull(s.find(DownloadTransfer.pendingName("song.mp3")));
  }
  @Test public void identicalUserReplacementDoesNotGrantDeletionAuthority() throws Exception {
    byte[] bytes={1,2}; preserved(marked(bytes,null),bytes,new DownloadTransfer.Control());
  }
  @Test public void corruptMarkerPreservesDocument() throws Exception {
    byte[] bytes={8,9}; preserved(marked(bytes,"corrupt\nmarker"),bytes,new DownloadTransfer.Control());
  }
  @Test public void missingOwnershipMetadataPreservesDocument() throws Exception {
    byte[] bytes={8,9}; preserved(marked(bytes,""),bytes,new DownloadTransfer.Control());
  }
  @Test public void externallyDeletedDocumentCanRetryWithoutDeletingOtherMusic() throws Exception {
    DownloadTransferTest.Filesystem s=marked(null,null);
    Files.write(new File(s.directory,"other.mp3").toPath(),new byte[]{9});
    try (DownloadTransferTest.Server server=new DownloadTransferTest.Server("Content-Length: 4\r\n",new byte[]{1,2,3,4})) {
      assertEquals("success",DownloadTransfer.download("deleted",server.url(),"song.mp3",s,
          new DownloadTransfer.Control(),(read,total)->{}).status);
    }
    assertArrayEquals(new byte[]{9},Files.readAllBytes(new File(s.find("other.mp3")).toPath()));
  }
  @Test public void failedContentVerificationKeepsMarkerAndBytes() throws Exception {
    byte[] bytes={7,7,7}; DownloadTransferTest.Filesystem s=marked(bytes,null);
    preserved(s,bytes,new DownloadTransfer.Control());
    assertNotNull(s.find(DownloadTransfer.pendingName("song.mp3")));
  }
  @Test public void recoveryCancellationNeverDeletesUserFile() throws Exception {
    byte[] bytes={9,9}; DownloadTransfer.Control c=new DownloadTransfer.Control();
    DownloadTransferTest.Filesystem s=new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public InputStream read(String path) throws IOException {
        if (path.endsWith(".pending")) c.requestCancel();
        return super.read(path);
      }
    };
    Files.write(new File(s.directory,"song.mp3").toPath(),bytes);
    Files.write(new File(s.directory,DownloadTransfer.pendingName("song.mp3")).toPath(),new File(s.directory,"song.mp3").getAbsolutePath().getBytes("UTF-8"));
    preserved(s,bytes,c);
    assertNotNull(s.find(DownloadTransfer.pendingName("song.mp3")));
  }
  private void metadataFailure(String mode) throws Exception {
    DownloadTransferTest.Filesystem s=new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public String name(String path) {
        if (path.endsWith("song.mp3")) {
          if (mode.equals("null")) return null;
          if (mode.equals("mismatch")) return "provider-renamed.mp3";
          throw new IllegalStateException("query failed");
        }
        return super.name(path);
      }
      public boolean delete(String path) {
        if(path.endsWith("song.mp3")) {
          if(mode.equals("deleteThrows"))throw new IllegalStateException("delete failed");
          return false;
        }
        return super.delete(path);
      }
    };
    try(DownloadTransferTest.Server server=new DownloadTransferTest.Server("Content-Length: 4\r\n",new byte[]{1,2,3,4})) {
      try {DownloadTransfer.download("name",server.url(),"song.mp3",s,new DownloadTransfer.Control(),(read,total)->{});fail("cannot publish");}
      catch(IOException expected){}
    }
    String marker=s.find(DownloadTransfer.pendingName("song.mp3"));
    assertNotNull(marker);assertNotNull(s.find("song.mp3"));
    assertEquals(s.find("song.mp3"),DownloadTransfer.readMarker(s,marker));
    // Fresh storage instance represents restart; retry cannot remove/overwrite the fragment.
    DownloadTransferTest.Filesystem restarted=new DownloadTransferTest.Filesystem(s.directory);
    preserved(restarted,new byte[0],new DownloadTransfer.Control());
    assertNotNull(restarted.find(DownloadTransfer.pendingName("song.mp3")));
  }
  @Test public void nameQueryExceptionRetainsCreatedUri() throws Exception {metadataFailure("query");}
  @Test public void nullNameRetainsCreatedUri() throws Exception {metadataFailure("null");}
  @Test public void mismatchedNameRetainsCreatedUri() throws Exception {metadataFailure("mismatch");}
  @Test public void failedDeleteRetainsCreatedUriAcrossRestart() throws Exception {metadataFailure("deleteFalse");}
  @Test public void throwingDeleteCannotEraseProtection() throws Exception {metadataFailure("deleteThrows");}

  @Test public void originalTemporaryFragmentCanRecoverWithoutDeletingIt() throws Exception {
    DownloadTransferTest.Filesystem s=marked(null,null);
    File fragment=new File(s.directory,".splayer-old.part");Files.write(fragment.toPath(),new byte[]{8});
    Files.write(new File(s.directory,DownloadTransfer.pendingName("song.mp3")).toPath(),fragment.getAbsolutePath().getBytes("UTF-8"));
    try(DownloadTransferTest.Server server=new DownloadTransferTest.Server("Content-Length: 4\r\n",new byte[]{1,2,3,4})) {
      assertEquals("success",DownloadTransfer.download("fresh",server.url(),"song.mp3",s,new DownloadTransfer.Control(),(read,total)->{}).status);
    }
    assertArrayEquals(new byte[]{8},Files.readAllBytes(fragment.toPath()));
  }
  @Test public void inaccessibleContentIsNotTreatedAsDeleted() throws Exception {
    DownloadTransferTest.Filesystem s=new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public String name(String path){return path.endsWith("song.mp3")?null:super.name(path);}
      public InputStream read(String path)throws IOException{
        if(path.endsWith("song.mp3"))throw new IOException("provider temporarily unavailable");
        return super.read(path);
      }
    };
    byte[] bytes={9};Files.write(new File(s.directory,"song.mp3").toPath(),bytes);
    Files.write(new File(s.directory,DownloadTransfer.pendingName("song.mp3")).toPath(),new File(s.directory,"song.mp3").getAbsolutePath().getBytes("UTF-8"));
    preserved(s,bytes,new DownloadTransfer.Control());assertNotNull(s.find(DownloadTransfer.pendingName("song.mp3")));
  }
  @Test public void providerRenamedTemporaryAudioKeepsProtection() throws Exception {
    DownloadTransferTest.Filesystem s=new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public String create(String name)throws IOException{return super.create(name.endsWith(".part")?"renamed.mp3":name);}
    };
    try(DownloadTransferTest.Server server=new DownloadTransferTest.Server("Content-Length: 4\r\n",new byte[]{1,2,3,4})) {
      try{DownloadTransfer.download("rename",server.url(),"song.mp3",s,new DownloadTransfer.Control(),(read,total)->{});fail("must fail");}
      catch(IOException expected){}
    }
    assertNotNull(s.find("renamed.mp3"));
    String marker=s.find(DownloadTransfer.pendingName("song.mp3"));assertNotNull(marker);
    assertEquals(s.find("renamed.mp3"),DownloadTransfer.readMarker(s,marker));
  }
  @Test public void newJournalDetectsTruncationEvenWhenUriPrefixIsValid() throws Exception {
    DownloadTransferTest.Filesystem s=new DownloadTransferTest.Filesystem(temporary.newFolder()) {
      public String name(String path){return path.endsWith("song.mp3")?null:super.name(path);}
    };
    try(DownloadTransferTest.Server server=new DownloadTransferTest.Server("Content-Length: 4\r\n",new byte[]{1,2,3,4})) {
      try{DownloadTransfer.download("journal",server.url(),"song.mp3",s,new DownloadTransfer.Control(),(read,total)->{});fail("must fail metadata");}
      catch(IOException expected){}
    }
    File marker=new File(s.find(DownloadTransfer.pendingName("song.mp3")));
    byte[] journal=Files.readAllBytes(marker.toPath());
    assertTrue("new journal must be framed, not a bare URI",new String(journal,"UTF-8").startsWith("SPLAYER-PENDING-1\n"));
    Files.write(marker.toPath(),java.util.Arrays.copyOf(journal,journal.length-2));
    try{DownloadTransfer.readMarker(s,marker.getAbsolutePath());fail("truncated marker must be rejected");}
    catch(IOException expected){}
    assertNotNull(s.find("song.mp3"));
  }
}
