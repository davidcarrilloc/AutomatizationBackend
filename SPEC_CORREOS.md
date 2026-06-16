Necesito que crees una automatizacion con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y clausulas necesarias establecidas en CLAUDE.md
- Dado un zip con varios exceles (de mil rows cada excel + encabezado) que tiene una sola columna (con titulo, favor de que al leerlo lo obvies), leer los textos que vengan de la siguiente
estructura del excel, el input es:
Correo
test.ccheck.app000003@gmail.com
test.ccheck.app000004@gmail.com
test.ccheck.app000005@gmail.com
... etc
- Los excel seran:
dentro de un zip vendra:
Segmento_001.xlsx
Segmento_002.xlsx
hasta
Segmento_116.xlsx
- Vamos a ocupar la siguiente query de bridgecore:
````
SELECT
        tx.error_detail,
        tx.id_tipo_tx,
   	    tip.atg_ship_grp_id,
   	    tip.atg_order_id,
	    tip.orden_venta,
	    tc.customer_email,
	    tip.id,
	    tip.id_cat_estatus,
	    tip.pedido,
	    tip.fecha_tx_compra,
	    tip.boleta,
	    tip.terminal,
	    tip.remision,
	    tip.orden_venta,
	    tip.total_cobrado,
	    tip.total_original,
	    tip.atg_order_id,
	    tip.atg_ship_grp_id,
	    tip.is_mkp,
	    tip.zip_code,
	    tip.recognition_store,
	    tip.recognition_store_channel,
	    tip.recognition_store_sub_channel,
	    tip.tienda_cliente
FROM BRIDGECORE.tx_informacion_procesada tip
INNER JOIN  BRIDGECORE.tx tx on tip.id = tx.id_info_procesada
INNER JOIN  BRIDGECORE.tx_cliente tc on tx.id_client = tc.id
WHERE 1=1
and tc.customer_email IN (
'test.ccheck.app000006@gmail.com',
'test.ccheck.app000002@gmail.com' -- aqui van los correos que se vayan leyendo de los excels, de mil en mil
)
-- AND tip.id_cat_estatus=0
-- AND tx.id_tipo_tx=1
order by tip.fecha_tx_compra desc;
````
- Vamos a obtener los datos y vaciarlos en un excel con la siguiente estructura:
error_detail | id_tipo_tx | atg_ship_grp_id | atg_order_id | orden_venta | customer_email | id | id_cat_estatus | pedido | fecha_tx_compra | boleta | terminal | remision | orden_venta | total_cobrado | total_original | atg_order_id | atg_ship_grp_id | is_mkp | zip_code | recognition_store | recognition_store_channel | recognition_store_sub_channel | tienda_cliente

Toma en cuenta los limites de excel y puedes truncar el JSON o texto completo para que no falle

