package org.example.parser.wb;

final class WildberriesProductDetailUrlBuilder {

    private WildberriesProductDetailUrlBuilder() {
    }

    static String build(String article) {
        return "https://www.wildberries.ru/__internal/u-card/cards/v4/detail"
                + "?appType=1"
                + "&curr=rub"
                + "&dest=-1257786"
                + "&spp=30"
                + "&hide_vflags=4294967296"
                + "&ab_testing=false"
                + "&lang=ru"
                + "&nm=" + article;
    }

    static String cardUrl(String article) {
        return "https://www.wildberries.ru/catalog/" + article + "/detail.aspx";
    }
}
