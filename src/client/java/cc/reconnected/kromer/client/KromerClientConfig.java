package cc.reconnected.kromer.client;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "rcc-kromer")
class KromerClientConfig implements ConfigData {
    boolean balanceDisplay = true;
    boolean toastPopup = true;
}