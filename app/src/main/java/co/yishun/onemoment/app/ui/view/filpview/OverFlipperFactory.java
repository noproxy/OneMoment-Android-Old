package co.yishun.onemoment.app.ui.view.filpview;

public class OverFlipperFactory {

    static OverFlipper create(FlipView v, OverFlipMode mode) {
        switch (mode) {
            case GLOW:
                return new GlowOverFlipper(v);
            case RUBBER_BAND:
                return new RubberBandOverFlipper();
        }
        return null;
    }

}
