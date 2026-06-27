package run.halo.watermark;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Slf4j
@Component
public class WatermarkPlugin extends BasePlugin {

    public WatermarkPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        log.info("Image Watermark & WebP plugin started");
    }

    @Override
    public void stop() {
        log.info("Image Watermark & WebP plugin stopped");
    }
}
