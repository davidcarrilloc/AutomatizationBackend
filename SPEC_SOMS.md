Necesito que crees una automatizacion con las siguientes condiciones:

- Vas a pasar por todo el flujo, capas y clausulas necesarias establecidas en CLAUDE.md
- Dado un excel de una sola columna, leer los textos que vengan
- Vamos a ocupar el siguiente endpoint XML SOAP:
  http://livp.liverpool.com.mx:3540/wsoms/getOrden/
Request:
  <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:smow="http://www.SMOWS02P.SMWS201C.Request.com">
  <soapenv:Header/>
  <soapenv:Body>
  <smow:SMOWS02POperation>
  <smow:qry_orden_req>
  <smow:req_orden>0590112015</smow:req_orden>
  </smow:qry_orden_req>
  </smow:SMOWS02POperation>
  </soapenv:Body>
  </soapenv:Envelope>
Response:
  <SOAP-ENV:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:smow="http://www.SMOWS02P.SMWS201C.Request.com" xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
  <SOAP-ENV:Body>
  <SMOWS02POperationResponse xmlns="http://www.SMOWS02P.SMWS201C.Response.com">
  <qry_orden_resp>
  <cod_resp>0</cod_resp>
  <datos_orden>
  <id_nvo_soms>0070967676</id_nvo_soms>
  <id_mdm/>
  <orden>590112015</orden>
  <tipo_orden>0</tipo_orden>
  <fec_venta>2026-06-14</fec_venta>
  <estatus>INCO</estatus>
  <nomb_remite/>
  <destinatario>
  <nombre>CARLA YARISSA CATA</nombre>
  <apel_pat/>
  <apel_mat/>
  <tel_casa>5551332800</tel_casa>
  <tel_celular>0</tel_celular>
  <calle>0001 LIV LIVERPOOL CENTRO L CENTRO VENUS</calle>
  <num_ext>92</num_ext>
  <num_int/>
  <edif/>
  <edo>DF</edo>
  <deleg_mun>CUAUHTEMOC</deleg_mun>
  <colonia>CENTRO</colonia>
  <codpost>006060</codpost>
  </destinatario>
  <observaciones/>
  <nomb_tda_venta>INTERNET</nomb_tda_venta>
  <evento>0</evento>
  <orden_recogido>0</orden_recogido>
  <orden_armado>0</orden_armado>
  <entre_calle1/>
  <entre_calle2/>
  </datos_orden>
  <datos_articulos>
  <cdat_skus>
  <sku>1200562697</sku>
  <desc_prod>Sudadera ADIDAS M</desc_prod>
  <cant_comprada>1</cant_comprada>
  <cant_entregada>0</cant_entregada>
  <num_prov>0</num_prov>
  <nomb_prov/>
  <registro_modelo/>
  <estatus_entrega>INCO</estatus_entrega>
  <fec_entrega>1900-01-01</fec_entrega>
  <fec_entrega2/>
  <fec_surtido>1900-01-01</fec_surtido>
  <num_intentos>0</num_intentos>
  <causa_no_entrega/>
  <band_recalculo/>
  </cdat_skus>
  </datos_articulos>
  </qry_orden_resp>
  </SMOWS02POperationResponse>
  </SOAP-ENV:Body>
  </SOAP-ENV:Envelope>
- El nodo   <smow:req_orden>0590112015</smow:req_orden> sera remplazado por   <smow:req_orden>:remision</smow:req_orden> y ahi se enviara cada remision que se obtuvo del archivo excel, tiene que ser una peticion despues de otra peticion, toma en cuenta el tratamiento de OMSController
- Vas a devolver un excel con la siguiente estructura:
  Remision | Status Datos | Nodo Destinatario | Response

Remision: la remision mandada
Status Datos:
Debes verificar el nodo <nombre>, <calle> <codpost>, si tienen informacion esos nodos pon: "DATOS" si no "SIN DATOS"
Nodo Destinatario:
<destinatario>
<nombre>CARLA YARISSA CATA</nombre>
<apel_pat/>
<apel_mat/>
<tel_casa>5551332800</tel_casa>
<tel_celular>0</tel_celular>
<calle>0001 LIV LIVERPOOL CENTRO L CENTRO VENUS</calle>
<num_ext>92</num_ext>
<num_int/>
<edif/>
<edo>DF</edo>
<deleg_mun>CUAUHTEMOC</deleg_mun>
<colonia>CENTRO</colonia>
<codpost>006060</codpost>
</destinatario>
Response: todo el xml


En caso de dar error se debera guardar esa row como:
9845411876 | "error": "" | SIN DATOS | {XML completo de la respuesta}

Toma en cuenta los limites de excel y puedes truncar el JSON completo para que no falle

