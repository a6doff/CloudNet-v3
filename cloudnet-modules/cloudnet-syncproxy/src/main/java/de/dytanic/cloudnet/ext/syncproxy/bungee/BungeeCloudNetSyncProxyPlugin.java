package de.dytanic.cloudnet.ext.syncproxy.bungee;

import de.dytanic.cloudnet.common.Validate;
import de.dytanic.cloudnet.common.collection.Iterables;
import de.dytanic.cloudnet.common.collection.Maps;
import de.dytanic.cloudnet.common.document.gson.JsonDocument;
import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyConfiguration;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyConfigurationProvider;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyConstants;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyProxyLoginConfiguration;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyTabList;
import de.dytanic.cloudnet.ext.syncproxy.SyncProxyTabListConfiguration;
import de.dytanic.cloudnet.ext.syncproxy.bungee.listener.BungeeProxyLoginConfigurationImplListener;
import de.dytanic.cloudnet.ext.syncproxy.bungee.listener.BungeeProxyTabListConfigurationImplListener;
import de.dytanic.cloudnet.ext.syncproxy.bungee.listener.BungeeSyncProxyCloudNetListener;
import de.dytanic.cloudnet.wrapper.Wrapper;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

@Getter
public final class BungeeCloudNetSyncProxyPlugin extends Plugin {

  private static final DateFormat DATE_FORMAT = new SimpleDateFormat(
    "HH:mm:ss");

  @Getter
  private static BungeeCloudNetSyncProxyPlugin instance;

  /*= ---------------------------------------------------------------------- =*/

  private final Map<UUID, Integer> onlineCountOfProxies = Maps
    .newConcurrentHashMap();

  /*= ---------------------------------------------------------------------- =*/

  private volatile AtomicInteger tabListEntryIndex = new AtomicInteger(-1);

  private volatile String tabListHeader = null, tabListFooter = null;

  public BungeeCloudNetSyncProxyPlugin() {
    instance = this;
  }

  @Override
  public void onEnable() {
    initListeners();
    initOnlineCount();
    scheduleTabList();
  }

  @Override
  public void onDisable() {
    CloudNetDriver.getInstance().getEventManager()
      .unregisterListeners(getClass().getClassLoader());
    Wrapper.getInstance().unregisterPacketListenersByClassLoader(
      this.getClass().getClassLoader());
  }

  public boolean inGroup(ServiceInfoSnapshot serviceInfoSnapshot,
    SyncProxyProxyLoginConfiguration syncProxyProxyLoginConfiguration) {
    Validate.checkNotNull(serviceInfoSnapshot);
    Validate.checkNotNull(syncProxyProxyLoginConfiguration);

    return Iterables.contains(syncProxyProxyLoginConfiguration.getTargetGroup(),
      serviceInfoSnapshot.getConfiguration().getGroups());
  }

  public SyncProxyProxyLoginConfiguration getProxyLoginConfiguration() {
    for (SyncProxyProxyLoginConfiguration syncProxyProxyLoginConfiguration :
      SyncProxyConfigurationProvider.load().getLoginConfigurations()) {
      if (syncProxyProxyLoginConfiguration.getTargetGroup() != null &&
        Iterables.contains(syncProxyProxyLoginConfiguration.getTargetGroup(),
          Wrapper.getInstance().getServiceConfiguration().getGroups())) {
        return syncProxyProxyLoginConfiguration;
      }
    }

    return null;
  }

  public SyncProxyTabListConfiguration getTabListConfiguration() {
    for (SyncProxyTabListConfiguration syncProxyTabListConfiguration :
      SyncProxyConfigurationProvider.load().getTabListConfigurations()) {
      if (syncProxyTabListConfiguration.getTargetGroup() != null &&
        Iterables.contains(syncProxyTabListConfiguration.getTargetGroup(),
          Wrapper.getInstance().getServiceConfiguration().getGroups())) {
        return syncProxyTabListConfiguration;
      }
    }

    return null;
  }

  public void updateSyncProxyConfigurationInNetwork(
    SyncProxyConfiguration syncProxyConfiguration) {
    CloudNetDriver.getInstance().sendChannelMessage(
      SyncProxyConstants.SYNC_PROXY_CHANNEL_NAME,
      SyncProxyConstants.SYNC_PROXY_UPDATE_CONFIGURATION,
      new JsonDocument(
        "syncProxyConfiguration",
        syncProxyConfiguration
      )
    );
  }

  public int getSyncProxyOnlineCount() {
    int onlinePlayers = ProxyServer.getInstance().getOnlineCount();

    for (Map.Entry<UUID, Integer> entry : onlineCountOfProxies.entrySet()) {
      if (!Wrapper.getInstance().getServiceId().getUniqueId()
        .equals(entry.getKey())) {
        onlinePlayers += entry.getValue();
      }
    }

    return onlinePlayers;
  }

  public void setTabList(ProxiedPlayer proxiedPlayer) {
    if (tabListEntryIndex.get() == -1) {
      return;
    }

    try {
      Class<?> baseComponentClass = Class
        .forName("net.md_5.bungee.api.chat.BaseComponent");
      Class<?> textComponentClass = Class
        .forName("net.md_5.bungee.api.chat.TextComponent");
      Object array = Array.newInstance(baseComponentClass, 1);

      Method methodFromLegacyTest = textComponentClass
        .getMethod("fromLegacyText", String.class);
      methodFromLegacyTest.setAccessible(true);

      Method methodSetTabHeader = ProxiedPlayer.class
        .getDeclaredMethod("setTabHeader", array.getClass(),
          array.getClass());
      methodSetTabHeader.setAccessible(true);

      SyncProxyProxyLoginConfiguration syncProxyProxyLoginConfiguration = getProxyLoginConfiguration();

      methodSetTabHeader.invoke(
        proxiedPlayer,
        methodFromLegacyTest.invoke(null, tabListHeader != null ?
          replaceTabListItem(proxiedPlayer,
            syncProxyProxyLoginConfiguration,
            ChatColor
              .translateAlternateColorCodes('&', tabListHeader + ""))
          :
            ""
        ),
        methodFromLegacyTest.invoke(null, tabListFooter != null ?
          replaceTabListItem(proxiedPlayer,
            syncProxyProxyLoginConfiguration,
            ChatColor
              .translateAlternateColorCodes('&', tabListFooter + ""))
          :
            ""
        )
      );

    } catch (Exception ignored) {
    }
  }

  private String replaceTabListItem(ProxiedPlayer proxiedPlayer,
    SyncProxyProxyLoginConfiguration syncProxyProxyLoginConfiguration,
    String input) {
    return input
      .replace("%proxy%", Wrapper.getInstance().getServiceId().getName() + "")
      .replace("%proxy_uniqueId%",
        Wrapper.getInstance().getServiceId().getUniqueId().toString() + "")
      .replace("%server%",
        proxiedPlayer.getServer() != null ? proxiedPlayer.getServer()
          .getInfo().getName() : "")
      .replace("%online_players%",
        (
          syncProxyProxyLoginConfiguration != null
            ? getSyncProxyOnlineCount()
            : ProxyServer.getInstance().getOnlineCount()
        ) + "")
      .replace("%max_players%",
        (
          syncProxyProxyLoginConfiguration != null
            ? syncProxyProxyLoginConfiguration.getMaxPlayers() :
            proxiedPlayer.getPendingConnection().getListener()
              .getMaxPlayers()
        ) + "")
      .replace("%proxy_task_name%",
        Wrapper.getInstance().getServiceId().getTaskName() + "")
      .replace("%name%", proxiedPlayer.getName() + "")
      .replace("%ping%", proxiedPlayer.getPing() + "")
      .replace("%time%", DATE_FORMAT.format(System.currentTimeMillis()) + "");
  }

  /*= ------------------------------------------------------------------------------------------------------------------- =*/

  private void scheduleTabList() {
    SyncProxyTabListConfiguration syncProxyTabListConfiguration = getTabListConfiguration();

    if (syncProxyTabListConfiguration != null
      && syncProxyTabListConfiguration.getEntries() != null &&
      !syncProxyTabListConfiguration.getEntries().isEmpty()) {
      if (tabListEntryIndex.get() == -1) {
        tabListEntryIndex.set(0);
      }

      if ((tabListEntryIndex.get() + 1) < syncProxyTabListConfiguration
        .getEntries().size()) {
        tabListEntryIndex.incrementAndGet();
      } else {
        tabListEntryIndex.set(0);
      }

      SyncProxyTabList tabList = syncProxyTabListConfiguration.getEntries()
        .get(tabListEntryIndex.get());

      tabListHeader = tabList.getHeader();
      tabListFooter = tabList.getFooter();

      ProxyServer.getInstance().getScheduler().schedule(
        this,
        this::scheduleTabList,
        1000 / syncProxyTabListConfiguration.getAnimationsPerSecond(),
        TimeUnit.MILLISECONDS
      );
    } else {
      tabListEntryIndex.set(-1);
      ProxyServer.getInstance().getScheduler()
        .schedule(this, this::scheduleTabList, 500, TimeUnit.MILLISECONDS);
    }

    for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
      setTabList(player);
    }
  }

  private void initListeners() {
    CloudNetDriver.getInstance().getEventManager()
      .registerListener(new BungeeSyncProxyCloudNetListener());

    ProxyServer.getInstance().getPluginManager().registerListener(this,
      new BungeeProxyLoginConfigurationImplListener());
    ProxyServer.getInstance().getPluginManager().registerListener(this,
      new BungeeProxyTabListConfigurationImplListener());
  }

  private void initOnlineCount() {
    SyncProxyProxyLoginConfiguration syncProxyProxyLoginConfiguration = getProxyLoginConfiguration();

    if (syncProxyProxyLoginConfiguration != null
      && syncProxyProxyLoginConfiguration.getTargetGroup() != null) {
      for (ServiceInfoSnapshot serviceInfoSnapshot : CloudNetDriver
        .getInstance().getCloudServiceByGroup(
          syncProxyProxyLoginConfiguration.getTargetGroup())) {
        if ((serviceInfoSnapshot.getServiceId().getEnvironment()
          .isMinecraftBedrockProxy() ||
          serviceInfoSnapshot.getServiceId().getEnvironment()
            .isMinecraftJavaProxy()) &&
          serviceInfoSnapshot.getProperties().contains(
            SyncProxyConstants.SYNC_PROXY_SERVICE_INFO_SNAPSHOT_ONLINE_COUNT)) {
          getOnlineCountOfProxies()
            .put(serviceInfoSnapshot.getServiceId().getUniqueId(),
              serviceInfoSnapshot.getProperties().getInt(
                SyncProxyConstants.SYNC_PROXY_SERVICE_INFO_SNAPSHOT_ONLINE_COUNT));
        }
      }
    }
  }
}