package cc.reconnected.kromer;

import io.wispforest.owo.config.annotation.Config;

@Config(name = "rcc-kromer", wrapperName = "RccKromerConfig")
public class ConfigModel {

    // this is the indev postgres post-surrealdb kromer2 github repo verison
    public String KromerURL = "https://kromer.sad.ovh/";
    public String KromerKey = "anndemeulemeester";
    public float SupporterMultiplier = 1.5f;
    public float HourlyWelfare = 1.5f;
    public String SupporterGroup = "group.supportertier1";
}
