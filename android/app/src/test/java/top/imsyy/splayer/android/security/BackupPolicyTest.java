package top.imsyy.splayer.android.security;

import static org.junit.Assert.*;
import org.junit.Test;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.*;

/** Android documented backup domains, checked against actual packaged resource inputs. */
public class BackupPolicyTest {
  @Test public void webviewSecretsAreExcludedFromAllThreeBackupModes()throws Exception {
    for(String resource:Arrays.asList("backup_rules","data_extraction_rules")) {
      Document document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File("src/main/res/xml/"+resource+".xml"));
      NodeList excludes=document.getElementsByTagName("exclude");int webview=0;
      for(int i=0;i<excludes.getLength();i++) {
        Element rule=(Element)excludes.item(i);String domain=rule.getAttribute("domain"),path=rule.getAttribute("path");
        assertTrue("Unsupported backup domain: "+domain,Arrays.asList("root","file","database","sharedpref","external","device_root","device_file","device_database","device_sharedpref").contains(domain));
        assertFalse("Every exclusion needs a path",path.isEmpty());
        if(path.equals("app_webview/")){assertEquals("app_webview is a data-root sibling of files", "root",domain);webview++;}
        assertFalse("Native ordinary preferences must remain eligible",domain.equals("sharedpref")&&(path.equals(".")||path.equals("./")));
      }
      assertEquals(resource.equals("backup_rules")?1:2,webview);
    }
  }
}
