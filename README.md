# franco-system-backend-filial

Anotaciones de cambios discriminado por fechas

--------------------------------------------------------------------------------------------------------------------------------------
29-12-22 (only filial dev)

Creacion de tabla para guardar mensajes de rabbit que no se pueden enviar
  - CREATE TABLE configuraciones.rabbitmq_msg (
	id bigserial primary key,
	tipo_accion text,
	tipo_entidad text,
	entidad text,
	id_sucursal_origen numeric,
	data text,
	recibido_en_servidor boolean,
	recibido_en_filial boolean,
	key text
);

--------------------------------------------------------------------------------------------------------------------------------------
19-12-2022

Modificacion en enum delivery_estado; (not ok)
  - ALTER TYPE operaciones.delivery_estado ADD VALUE 'CONCLUIDO';


--------------------------------------------------------------------------------------------------------------------------------------

29-11-2022

  - Modificacion en venta credito.
  - Adicionar parametros ventaCreditoInput y ventaCreditoCuotaInputList a la funcion de guardado de venta
  - Como ahora la venta a credito ya se puede guardar localmente entonces podemos guardar todo en la misma funcion, creo yo jeje
  - Luego de guardar todo se puede propagar al servidor
  - Modificaciones en bd servidor central (ok)
    - ALTER TABLE financiero.venta_credito_cuota ADD sucursal_id int8 NULL; 
    - ALTER TABLE financiero.venta_credito_cuota ADD CONSTRAINT venta_credito_cuota_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON UPDATE CASCADE ON DELETE CASCADE;
    - ALTER TABLE financiero.venta_credito_cuota ADD CONSTRAINT venta_credito_cuota_usuario_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON UPDATE CASCADE ON DELETE SET NULL;
    - ALTER TABLE financiero.venta_credito_cuota DROP CONSTRAINT venta_credito_cuota_venta_credito_fk;
    - ALTER TABLE financiero.venta_credito_cuota ADD CONSTRAINT venta_credito_cuota_venta_credito_fk FOREIGN KEY (venta_credito_id) REFERENCES financiero.venta_credito(id) ON DELETE CASCADE ON UPDATE CASCADE;
    - ALTER TABLE financiero.venta_credito_cuota DROP CONSTRAINT venta_credito_cuota_fk;
    - ALTER TABLE financiero.venta_credito_cuota ADD CONSTRAINT venta_credito_cuota_fk FOREIGN KEY (usuario_id) REFERENCES personas.usuario(id) ON DELETE RESTRICT ON UPDATE CASCADE;
    - ALTER TABLE financiero.venta_credito_cuota DROP CONSTRAINT venta_credito_cuota_sucursal_fk;
    - ALTER TABLE financiero.venta_credito_cuota ADD CONSTRAINT venta_credito_cuota_sucursal_fk FOREIGN KEY (sucursal_id) REFERENCES empresarial.sucursal(id) ON DELETE SET NULL ON UPDATE CASCADE;

--------------------------------------------------------------------------------------------------------------------------------------


