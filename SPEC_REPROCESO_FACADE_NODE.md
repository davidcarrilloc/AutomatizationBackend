Necesito que crees una automatizacion con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y clausulas necesarias establecidas en CLAUDE.md
- Dado un excel de una columna, leer el json de la columna A
- Analizar si tiene OrderLines.OrderLine.Store de tal forma "Store": "F001", y remplazarlo por "Store": "001",
- Vamos a ocupar el siguiente endpoint
  curl --location 'https://apigee-pro.liverpool.com.mx/oms/sl/I200?origen=ecom' \
  --header 'apikey: wTFlLG22bZNEAQlKwbnk8KesSevGZASWi4XScdpYEmjG8Z0j' \
  --header 'Content-Type: application/json' \
  --data-raw '{
  {{AQUI VA EL JSON DE LA COLUMNA A CON LA CORRECCION DEL STORE}}
  }'

Los ejemplos se parecen mucho al SPEC_REPROCESO_FACADE.md
