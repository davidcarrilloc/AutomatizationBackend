Necesito que actuales una automatizacion con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y clausulas necesarias establecidas en CLAUDE.md
- Dado un excel que tiene una sola columna, leer los textos que vengan, estos seran los tracking numbers
- Vas a ocupar el controller actual de fulfillment controller: enviarFulfillment y vas a facilitar el envio masivo de la siguiente manera:
Si  response o el error que devuelve un endpoint es "Error consultando fulfillment para el tracking 6970114157: 500 Internal Server Error from POST https://ogcp-apigke-site-d.liverpool.com.mx/order-service/v1/order/fulFillment"
pausa la ejecucion 10 segundos y sigue avanzando con lo demas

Al ser un reproceso masivo, devuelveme un job y que pueda consultar cada que yo quiera el avance y me devuelvas el excel con las que se han procesado, adicional, todas las que tuvieron el error del gateway vuelvelas a reprocesar

Toma en cuenta los limites de excel y puedes truncar el JSON o texto completo para que no falle

