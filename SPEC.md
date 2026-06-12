Necesito que crees una automatizacion con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y clausulas necesarias establecidas en CLAUDE.md
- Dado un excel de una sola columna, leerlas los textos que vengan
- Vamos a ocupar el siguiente endpoint
curl --location 'https://ogcp-apigke-site-d.liverpool.com.mx/order-service/v1/order/fulFillment' \
--header 'Content-Type: application/json' \
--data '{
"processes":[
{"trackingNumber":"9845411876"}
    ]
}'
- El parametro trackingNumber debe ser reemplazado por cada uno de los textos que se encuentren en el excel, debes mandar a llamar uno por uno el API
La salida de la api es asi:
```
{
        "statusSoms": null,
        "statusMkp": null,
        "statusSterling": null,
        "statusPendingOrder": null,
        "statusOms": null,
        "statusProtec": null,
        "statusMyPurchases": null,
        "statusFirebase": null,
        "statusPreBackOrder": null,
        "statusEmail": null,
        "error": "Audit fulfillment validation failed for tracking number 4010111896",
        "trackingNumber": "4010111896",
        "orderId": null
    },
    {
        "statusSoms": null,
        "statusMkp": "SUCCESS",
        "statusSterling": null,
        "statusPendingOrder": "NOT_EXECUTE",
        "statusOms": "SUCCESS",
        "statusProtec": "NOT_EXECUTE",
        "statusMyPurchases": "SUCCESS",
        "statusFirebase": "SUCCESS",
        "statusPreBackOrder": "NOT_EXECUTE",
        "statusEmail": "SUCCESS",
        "error": null,
        "trackingNumber": "5070111746",
        "orderId": null
    },
```

- Una vez finalizado esto, la salida debera guardarse en un excel como:
TrackingNumber | Response | JSON
  9845411876 | [concatenado de todos lo success por ejemplo: "statusMkp": "SUCCESS", "statusOms": "SUCCESS", "statusMyPurchases": "SUCCESS", "statusFirebase": "SUCCESS", "statusEmail": "SUCCESS"] | {JSON completo de la respuesta}

En caso de dar error se debera guardar esa row como:
9845411876 | "error": "Audit fulfillment validation failed for tracking number 4010111896", | {JSON completo de la respuesta}

Toma en cuenta los limites de excel y puedes truncar el JSON completo para que no falle

