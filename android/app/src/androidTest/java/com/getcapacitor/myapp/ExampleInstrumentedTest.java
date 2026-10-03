package com.getcapacitor.myapp;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;

/** 验证实际应用上下文，兼容正式与隔离验证包 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {

    @Test
    public void useAppContext() throws Exception {
        // 校验 SPlayer 包名及构建后缀，不能沿用模板占位值
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        String packageName = appContext.getPackageName();
        assertTrue(packageName.equals("top.imsyy.splayer.android")
            || packageName.startsWith("top.imsyy.splayer.android."));
    }
}
