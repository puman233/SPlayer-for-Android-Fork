package top.imsyy.splayer.android.playback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class PlaybackService extends MediaSessionService {
  @Override
  public void onCreate() {
    super.onCreate();
    PlaybackManager.getInstance(this).attachService(this);
  }

  @Override
  public void onDestroy() {
    PlaybackManager.getInstance(this).detachService(this);
    super.onDestroy();
  }

  @Nullable
  @Override
  public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
    return PlaybackManager.getInstance(this).getSession();
  }
}
