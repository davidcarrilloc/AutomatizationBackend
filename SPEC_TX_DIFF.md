Necesito que crees una automatizacion con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y clausulas necesarias establecidas en CLAUDE.md
- Al mandar a llamar a la API deberá:
- Ejecutar las siguientes queries
-- PARA APP
-- llamemosla query "APP AYER"
select  to_char(tip.fecha_tx_compra,'HH24:MI'), count(tip.id)
from BRIDGECORE.TX TX
inner join BRIDGECORE.TX_INFORMACION_PROCESADA TIP ON TX.ID_INFO_PROCESADA = TIP.ID
where
1=1
and tip.fecha_tx_compra BETWEEN :start_date_ayer and :end_date_ayer
and TIP.ID_CAT_ESTATUS = 0
and TX.ID_TIPO_TX = 1
AND tx.ID_TIPO_ARTICULO IN (0,1)
AND tip.TIENDA_CLIENTE = 39
and tx.ID_CANAL IN (9) -- app
group by  to_char(tip.fecha_tx_compra,'HH24:MI')
ORDER BY to_char(tip.fecha_tx_compra,'HH24:MI') desc;

-- llamemosla query "APP HOY"
select  to_char(tip.fecha_tx_compra,'HH24:MI'), count(tip.id)
from BRIDGECORE.TX TX
inner join BRIDGECORE.TX_INFORMACION_PROCESADA TIP ON TX.ID_INFO_PROCESADA = TIP.ID
where
1=1
and tip.fecha_tx_compra BETWEEN :start_date_hoy and :end_date_hoy
and TIP.ID_CAT_ESTATUS = 0
and TX.ID_TIPO_TX = 1
AND tx.ID_TIPO_ARTICULO IN (0,1)
AND tip.TIENDA_CLIENTE = 39
and tx.ID_CANAL IN (9) -- app
group by  to_char(tip.fecha_tx_compra,'HH24:MI')
ORDER BY to_char(tip.fecha_tx_compra,'HH24:MI') desc;

-- PARA WEB
-- llamemosla query "WEB AYER"
select  to_char(tip.fecha_tx_compra,'HH24:MI'), count(tip.id)
from BRIDGECORE.TX TX
inner join BRIDGECORE.TX_INFORMACION_PROCESADA TIP ON TX.ID_INFO_PROCESADA = TIP.ID
where
1=1
and tip.fecha_tx_compra BETWEEN :start_date_ayer and :end_date_ayer
and TIP.ID_CAT_ESTATUS = 0
and TX.ID_TIPO_TX = 1
AND tx.ID_TIPO_ARTICULO IN (0,1)
AND tip.TIENDA_CLIENTE = 39
AND tx.ID_CANAL IN (1,8) --web
group by  to_char(tip.fecha_tx_compra,'HH24:MI')
ORDER BY to_char(tip.fecha_tx_compra,'HH24:MI') desc;

-- llamemosla query "WEB HOY"
select  to_char(tip.fecha_tx_compra,'HH24:MI'), count(tip.id)
from BRIDGECORE.TX TX
inner join BRIDGECORE.TX_INFORMACION_PROCESADA TIP ON TX.ID_INFO_PROCESADA = TIP.ID
where
1=1
and tip.fecha_tx_compra BETWEEN :start_date_hoy and :end_date_hoy
and TIP.ID_CAT_ESTATUS = 0
and TX.ID_TIPO_TX = 1
AND tx.ID_TIPO_ARTICULO IN (0,1)
AND tip.TIENDA_CLIENTE = 39
AND tx.ID_CANAL IN (1,8) --web
group by  to_char(tip.fecha_tx_compra,'HH24:MI')
ORDER BY to_char(tip.fecha_tx_compra,'HH24:MI') desc;

- Al obtener los datos, dibuja los siguientes graficos:
Grafico 1: Comparativa de ventas por hora entre ayer y hoy para APP
Grafico 2: Comparativa de ventas por hora entre ayer y hoy para WEB

En caso de dar error se debera regresar un json indicando el error
Toma en cuenta los limites de excel y puedes truncar las strings completo para que no falle

