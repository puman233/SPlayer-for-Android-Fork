package top.imsyy.splayer.android.download;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/** A real SAF provider without rename support; never exposes user files. */
public final class DownloadFixtureProvider extends DocumentsProvider {
  static final String AUTHORITY = "top.imsyy.splayer.android.debug.test.downloadfixture";
  private File base;
  private volatile boolean failWrite;
  private volatile int deleteDelay;
  private volatile int renameCalls;
  private volatile boolean failAudioName, failAudioDelete;
  private volatile boolean renameAudioOnCreate;
  @Override public boolean onCreate() {
    base = new File(getContext().getCacheDir(), "download-fixtures");
    try { base = base.getCanonicalFile(); } catch (IOException e) { return false; }
    return base.exists() || base.mkdirs();
  }
  private File file(String id) throws FileNotFoundException {
    try {
      File result = new File(base, id).getCanonicalFile();
      if (!result.getPath().startsWith(base.getCanonicalPath() + File.separator)) throw new IOException("Outside fixture");
      return result;
    } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
  }
  private String id(File f) { return f.getAbsolutePath().substring(base.getAbsolutePath().length() + 1); }
  private String[] columns(String[] p) {
    return p != null ? p : new String[]{Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};
  }
  private void row(MatrixCursor c, File f) {
    MatrixCursor.RowBuilder r = c.newRow();
    for (String col : c.getColumnNames()) {
      Object v = null;
      if (col.equals(Document.COLUMN_DOCUMENT_ID)) v = id(f);
      else if (col.equals(Document.COLUMN_DISPLAY_NAME)) v = f.getName();
      else if (col.equals(Document.COLUMN_MIME_TYPE)) v = f.isDirectory() ? Document.MIME_TYPE_DIR : (f.getName().endsWith(".wav") ? "audio/wav" : "application/octet-stream");
      else if (col.equals(Document.COLUMN_FLAGS)) v = Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_WRITE | (f.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : 0);
      else if (col.equals(Document.COLUMN_SIZE)) v = f.length();
      else if (col.equals(Document.COLUMN_LAST_MODIFIED)) v = f.lastModified();
      r.add(v);
    }
  }
  @Override public Cursor queryRoots(String[] projection) {
    MatrixCursor c = new MatrixCursor(new String[]{Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS});
    return c;
  }
  @Override public Cursor queryDocument(String id, String[] projection) throws FileNotFoundException {
    if (failAudioName && id.endsWith(".wav")) throw new FileNotFoundException("Injected audio metadata failure");
    MatrixCursor c = new MatrixCursor(columns(projection));
    File f = file(id); if (f.exists()) row(c, f); return c;
  }
  @Override public Cursor queryChildDocuments(String id, String[] projection, String sort) throws FileNotFoundException {
    MatrixCursor c = new MatrixCursor(columns(projection));
    File[] children = file(id).listFiles(); if (children != null) for (File f : children) row(c, f); return c;
  }
  @Override public boolean isChildDocument(String parent, String child) {
    try { return file(child).getPath().startsWith(file(parent).getPath() + File.separator); }
    catch (FileNotFoundException e) { return false; }
  }
  @Override public String createDocument(String parent, String mime, String name) throws FileNotFoundException {
    if (name.contains("/") || name.contains("\\") || name.equals(".") || name.equals("..")) throw new FileNotFoundException("Unsafe fixture name");
    File f = file(parent + "/" + (renameAudioOnCreate && name.endsWith(".wav") ? "renamed.wav" : name));
    try {
      if (!(Document.MIME_TYPE_DIR.equals(mime) ? f.mkdir() : f.createNewFile())) throw new IOException("Exists");
      return id(f);
    } catch (IOException e) { throw new FileNotFoundException(e.toString()); }
  }
  @Override public ParcelFileDescriptor openDocument(String id, String mode, CancellationSignal signal) throws FileNotFoundException {
    File f = file(id);
    if (failWrite && mode.contains("w") && f.getName().endsWith(".part")) throw new FileNotFoundException("Injected provider write failure");
    return ParcelFileDescriptor.open(f, ParcelFileDescriptor.parseMode(mode));
  }
  @Override public void deleteDocument(String id) throws FileNotFoundException {
    File f = file(id);
    if (failAudioDelete && f.getName().endsWith(".wav")) throw new FileNotFoundException("Injected audio delete failure");
    if (f.getName().endsWith(".part") && deleteDelay > 0) android.os.SystemClock.sleep(deleteDelay);
    if (!f.delete()) throw new FileNotFoundException("Fixture delete failed");
  }
  @Override public String renameDocument(String id, String name) throws FileNotFoundException {
    renameCalls++; throw new FileNotFoundException("Provider does not support rename");
  }
  @Override public Bundle call(String method, String arg, Bundle extras) {
    if (!method.startsWith("fixture:")) return super.call(method, arg, extras);
    Bundle result = new Bundle();
    try {
      if (method.equals("fixture:configure")) {
        failWrite = extras.getBoolean("failWrite"); deleteDelay = extras.getInt("deleteDelay");
        failAudioName = extras.getBoolean("failAudioName"); failAudioDelete = extras.getBoolean("failAudioDelete");
        renameAudioOnCreate = extras.getBoolean("renameAudioOnCreate");
      } else if (method.equals("fixture:root") || method.equals("fixture:grant")) {
        File root = file(arg);
        if (method.equals("fixture:root") ? !root.mkdir() : !root.isDirectory()) throw new IOException("Invalid fixture root");
        getContext().grantUriPermission("top.imsyy.splayer.android.debug",
            android.provider.DocumentsContract.buildTreeDocumentUri(AUTHORITY,arg),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION | android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
      } else if (method.equals("fixture:put")) {
        File f = file(arg); if (!f.createNewFile()) throw new IOException("Refusing overwrite");
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(extras.getByteArray("bytes")); }
      } else if (method.equals("fixture:replace")) {
        File f = file(arg); if (!f.isFile()) throw new IOException("Only existing isolated fixture documents");
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(extras.getByteArray("bytes")); }
      }
      result.putInt("renameCalls", renameCalls);
    } catch (IOException e) { throw new IllegalArgumentException(e); }
    return result;
  }
}
