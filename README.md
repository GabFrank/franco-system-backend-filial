# franco-system-backend-filial

Anotaciones de cambios discriminado por fechas

29-11-2022

  - Modificacion en venta credito.
  - Adicionar parametros ventaCreditoInput y ventaCreditoCuotaInputList a la funcion de guardado de venta
  - Como ahora la venta a credito ya se puede guardar localmente entonces podemos guardar todo en la misma funcion, creo yo jeje
  - Luego de guardar todo se puede propagar al servidor
  - Modificaciones en bd servidor central (ok)
    - ALTER TABLE financiero.venta_credito_cuota ADD sucursal_id int8 NULL; 
    - ALTER TABLE financiero.venta_credito_cuota ADD CONSTRAINT venta_credito_cuota_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON UPDATE CASCADE ON DELETE CASCADE;
    - ALTER TABLE financiero.venta_credito_cuota ADD CONSTRAINT venta_credito_cuota_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON UPDATE CASCADE ON DELETE SET NULL;


