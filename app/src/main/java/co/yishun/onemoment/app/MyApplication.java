package co.yishun.onemoment.app;

import android.app.Application;
import org.androidannotations.annotations.EApplication;
import quickutils.core.QuickUtils;

/**
 * Created by Carlos on 2/3/15.
 */
@EApplication
public class MyApplication extends Application {
    public boolean isRelease = true;

    @Override
    public void onCreate() {
        super.onCreate();
        QuickUtils.init(this);

    }
}
