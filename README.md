# franco-system-backend-filial

Anotaciones de cambios discriminado por fechas

29-11-2022

  - Modificacion en venta credito.
  - Adicionar parametros ventaCreditoInput y ventaCreditoCuotaInputList a la funcion de guardado de venta
  - Como ahora la venta a credito ya se puede guardar localmente entonces podemos guardar todo en la misma funcion, creo yo jeje
  - Luego de guardar todo se puede propagar al servidor
  - Modificaciones en base de datos central
    - ALTER TABLE financiero.venta_credito_cuota ADD sucursal_id int8 NULL;

