package de.dytanic.cloudnet.ext.bridge.sponge;

import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.ext.bridge.BridgeHelper;
import de.dytanic.cloudnet.ext.bridge.listener.BridgeCustomChannelMessageListener;
import de.dytanic.cloudnet.ext.bridge.sponge.listener.SpongeCloudNetListener;
import de.dytanic.cloudnet.ext.bridge.sponge.listener.SpongePlayerListener;
import de.dytanic.cloudnet.ext.bridge.sponge.util.SpongeCloudNetAdapterClassLoader;
import de.dytanic.cloudnet.wrapper.Wrapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.plugin.Plugin;

@Plugin(
  id = "cloudnet_bridge",
  name = "CloudNet-Bridge",
  version = "1.0",
  description = "Sponge extension for the CloudNet runtime, which optimize some features",
  url = "https://cloudnetservice.eu"
)
public final class SpongeCloudNetBridgePlugin {

  @Listener
  public synchronized void handle(GameStartedServerEvent event) {
    try {
      Method method = ClassLoader.getSystemClassLoader().getClass()
        .getMethod("getCloudNetWrapperClassLoader");
      method.setAccessible(true);

      ClassLoader classLoader = new SpongeCloudNetAdapterClassLoader(
        (ClassLoader) method.invoke(ClassLoader.getSystemClassLoader()),
        Thread.currentThread().getContextClassLoader(),
        ClassLoader.getSystemClassLoader().getParent()
      );

      Field field = ClassLoader.class.getDeclaredField("parent");
      field.setAccessible(true);
      field.set(Thread.currentThread().getContextClassLoader(), classLoader);

      field = ClassLoader.class.getDeclaredField("scl");
      field.setAccessible(true);
      field.set(null, Thread.currentThread().getContextClassLoader());

    } catch (Exception ex) {
      ex.printStackTrace();
    }

    Sponge.getChannelRegistrar().createChannel(this, "bungeecord:main");
    Sponge.getChannelRegistrar().createChannel(this, "cloudnet:main");

    this.initListeners();
    BridgeHelper.updateServiceInfo();
  }

  @Listener
  public synchronized void handle(GameStoppingServerEvent event) {
    Sponge.getEventManager().unregisterListeners(this);
    CloudNetDriver.getInstance().getEventManager()
      .unregisterListeners(this.getClass().getClassLoader());
    Wrapper.getInstance().unregisterPacketListenersByClassLoader(
      this.getClass().getClassLoader());
  }

  private void initListeners() {
    //Sponge API
    Sponge.getEventManager()
      .registerListeners(this, new SpongePlayerListener());

    //CloudNet
    CloudNetDriver.getInstance().getEventManager()
      .registerListener(new SpongeCloudNetListener());
    CloudNetDriver.getInstance().getEventManager()
      .registerListener(new BridgeCustomChannelMessageListener());
  }
}