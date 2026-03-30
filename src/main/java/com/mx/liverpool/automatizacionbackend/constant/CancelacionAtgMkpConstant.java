package com.mx.liverpool.automatizacionbackend.constant;

public enum CancelacionAtgMkpConstant {
    ORDER_ID(""),
    SHIPPING_GROUP_ID(""),
    CREATION_DATE("23/06/2025"),
    ID_CYBERSOURCE("11111"),
    CUSTOMER("99999999"),
    FIRST_NAME("Liverpool"),
    LAST_NAME("Digital"),
    BILLINGADDRESS(""),
    DECODE(
            "DECODE(BILL.FIRST_NAME,NULL,SHIPP.FIRST_NAME,BILL.FIRST_NAME)", "Liverpool", // 0,1
            "DECODE(BILL.LAST_NAME,NULL,SHIPP.LAST_NAME,BILL.LAST_NAME)", "Digital", // 2,3
            "DECODE(BILL.POSTAL_CODE,NULL,SHIPP.POSTAL_CODE,BILL.POSTAL_CODE)", "05348", // 4,5
            "DECODE(BILL.CITY,NULL,SHIPP.CITY,BILL.CITY)", "MEXICO", // 6,7
            "DECODE(BILL.STATE_NAME,NULL,SHIPP.STATE_NAME,BILL.STATE_NAME)", "MEXICO", // 8,9
            "DECODE(BILL.HOME_PHONE,NULL,SHIPP.HOME_PHONE,BILL.HOME_PHONE)", "52-55555555", // 10,11
            "DECODE(USR.EMAIL,NULL,'MARKETPLACE@LIVERPOOL.COM.MX',USR.EMAIL)", // 12,13
            "DECODE(SHIPP.STREET,NULL,'NA',SHIPP.STREET)", "Mario Pani", // 14,15
            "DECODE(SHIPP.EXTERIOR_NUMBER,NULL,'NA',SHIPP.EXTERIOR_NUMBER)", "s/n", // 16,17
            "DECODE(SHIPP.INTERIOR_NUMBER,NULL,'NA',SHIPP.INTERIOR_NUMBER)", "NA", // 18,19
            "DECODE(SHIPP.NEIGHBORHOOD,NULL,'NA',SHIPP.NEIGHBORHOOD)", "Contadero", // 20,21
            "DECODE(SHIPP.MUNICIPALITY,NULL,'NA',SHIPP.MUNICIPALITY)", "Cuajimalpa", // 22,23
            "CASEWHENSKU.PRODUCT_TYPE_REF=0THEN'0'ELSE'1'END", "1" // 24,25
    ),
    COUNTRY("MX"),
    ISO_CODE("MEX"),
    LOCALE("es_mx"),
    STREET_1("Calle: Mario Pani , Numero:200"),
    STREET_2("Municipio: CUAJIMALPA"),
    CIERRE1(""),
    CIRRE1_2(""),
    FIRST_NAME_1("Liverpool"),
    LAST_NAME_1("Digital"),
    POSTAL_CODE("05348"),
    CITY("Contadero"),
    COUNTRY_SH("MX"),
    COUNTRY_ISO_SH("MEX"),
    STATE_NAME("MEXICO"),
    HOME_PHONE("52-55555555"),
    STREET_1SH("Calle: Mario Pani , Numero:200"),
    STREET_2SH("Colonia: Contadero, Municipio: Cuajimalpa"),
    CIERRE2(""),
    PROFILE_ID("99999999"),
    CIERRRE3(""),
    OFFEROPEN(""),
    QUANTITY("1"),
    BRIDGECORE_SKU_AMOUNT("1999"),
    SHIPPING_PRICE("0"),
    SHIPPINGTYPE("Home"),
    ORDER_ADD(""),
    CODE_PROMO("liver-promo-label"),
    TYPE_PROMO("STRING"),
    VALUE_PROMO("PMR('PAGO UNICO')"),
    CIERRE4(""),
    CODE_PRICE("offer-original-price"),
    TYPE_PRICE("NUMERIC"),
    LIST_PRICE("1499"),
    CIERRE5(""),
    SKU_CODE("product-sap-sku-id"),
    SKU_TYPE("STRING"),
    SKU("1168757421"),
    CIERRE6(""),
    CODE_REEM("reembolso-parcial"),
    VALUE_CODE("FALSE"),
    CIERRE7(""),
    CODE_ART("tipo-articulo"),
    TIPO_ART("STRING"),
    PRODUCTTYPE("SL"),
    CIERRE8(""),
    TAXES("null"),
    CURR_ISO_CODE("MXN"),
    LEADTIME("1"),
    OFFERID("1112324592"),
    OFFER_PRICE("1999"),
    CIERRE9(""),
    PAYAMENT_INFO(""),
    PAYMETNMETHOD("Dilisa"),
    AUTHORIZATION_NUMBER("11111"),
    EDIFICIO(""),
    ENTRE_CALLES("");

    private final String[] values;

    private CancelacionAtgMkpConstant(String... values) {
        this.values = values;
    }

    public String getValue() {
        return (values.length > 0) ? values[0] : "";
    }

    public String[] getValues() {
        return values;
    }
}