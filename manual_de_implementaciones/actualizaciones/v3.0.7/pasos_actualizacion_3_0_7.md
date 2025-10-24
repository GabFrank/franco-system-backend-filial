### En el servidor filial ejecutar estos comandos
#### Bajar el jar y el pfx
curl -L -o /home/franco/FRC/frc-server/frc-server.jar https://github.com/GabFrank/franco-system-backend-filial/releases/download/v3.0.7/frc-server.jar
mkdir /home/franco/FRC/certificados/
curl -L -o /home/franco/FRC/certificados/certificado.pfx https://github.com/GabFrank/franco-system-backend-filial/releases/download/v3.0.7/certificado.pfx
#### Adicionar propiedades al app properties
cat <<'EOF' >> /home/franco/FRC/frc-server/application.properties
# Habilita el módulo de SIFEN
sifen.enabled=true

# Habilita las tareas programadas de SIFEN (consultas, envío de lotes, etc.)
sifen.scheduler.enabled=true

# Ruta completa al archivo del certificado digital
sifen.certificado.archivo=/home/franco/FRC/certificados/certificado.pfx

# Contraseña del certificado digital
sifen.certificado.contrasena=fasa1701

# Código de Seguridad del Contribuyente (CSC)
sifen.csc=D37561586c1CAd69A2e7747E73f9F03B

# Habilitar nota tecnica 13
sifen.habilitar-nota-tecnica-13=true

# Delay del scheduler de sifen (10 minutos por efecto)
sifen.scheduler.fixed-delay=600000

EOF

### reiniciar el servidor y ver los logs
sudo systemctl restart frc && journalctl -f -u frc

### agregar los datos de timbrado y timbrado detalle faltantes para los que van a tener facturacion electronica
#### ejemplo para la sucursal 24
#### actualizar el timbrado
-- timbrado (id = 6)
UPDATE financiero.timbrado
SET
  razon_social = 'FRANCO AREVALOS S.A.',
  ruc = '80099482',
  numero = '18270044',
  is_electronico = true,
  csc = 'D37561586c1CAd69A2e7747E73f9F03B',
  fecha_inicio = '2025-08-29 00:00:00',
  fecha_fin = '2027-08-29 00:00:00',
  email = 'francoarevalos05@gmail.com',
  tipo_sociedad = 'S.A.',
  domicilio_fiscal_departamento = 'CANINDEYU',
  domicilio_fiscal_ciudad = 'SALTO DEL GUAIRA',
  domicilio_fiscal_codigo_ciudad = '4738',
  domicilio_fiscal_localidad = NULL,
  domicilio_fiscal_barrio = NULL,
  domicilio_fiscal_direccion = 'CALLE, PY 03 GENERAL ELIZARDO AQUINO',
  telefono = '0982700027',
  cod_actividad_economica_principal = '46304',
  desc_actividad_economica_principal = 'COMERCIO AL POR MAYOR DE BEBIDAS',
  list_codigo_actividad_economica_secundaria = '47112',
  list_descripcion_actividad_economica_secundaria = 'COMERCIO AL POR MENOR EN MINI MERCADOS Y DESPENSAS',
  activo = true,
  usuario_id = 1
WHERE id = 6;

#### actualizar timbrado detalle para sucursal 24. Obs: cambiar los datos
-- timbrado_detalle (id = 93)
UPDATE financiero.timbrado_detalle
SET
  timbrado_id = 5,
  punto_de_venta_id = 25,
  sucursal_id = 24,
  punto_expedicion = '001',
  cantidad = 100000,
  rango_desde = 0,
  rango_hasta = 100000,
  numero_actual = 77,
  departamento = 'CANINDEYU',
  ciudad = 'SALTO DEL GUAIRA',
  codigo_ciudad = '4738',
  localidad = NULL,
  barrio = NULL,
  direccion = 'CALLE, PY 03 GENERAL ELIZARDO AQUINO',
  telefono = '0982700027',
  activo = true
WHERE id = 93;

### Actualizar app
rm /home/franco/FRC/FRC.AppImage
curl -L -o /home/franco/FRC/FRC.AppImage https://github.com/GabFrank/frc-sistemas-integrados-angular/releases/download/V3.0.7/FRC.AppImage
chmod a+x /home/franco/FRC/FRC.AppImage

facturas:
75 ok

80 ok
81 ok
82 ok
83 ok
84 ok
85 ok
86 ok
87 ok
88 --
89 ok
90 ok
91 ok
92 ok
93 ok
