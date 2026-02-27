### En el servidor filial ejecutar estos comandos

#### Adicionar propiedades al app properties
cat <<'EOF' >> /home/franco/FRC/frc-server/application.properties
# Habilita el módulo de SIFEN
sifen.enabled=false

# Habilita las tareas programadas de SIFEN (consultas, envío de lotes, etc.)
sifen.scheduler.enabled=false

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

# Mejora de firewall
server.address:0.0.0.0

EOF
#### Bajar el jar y el pfx
mkdir /home/franco/FRC/certificados/
curl -L -o /home/franco/FRC/certificados/certificado.pfx https://github.com/GabFrank/franco-system-backend-filial/releases/download/v3.0.7/certificado.pfx

cp /home/franco/FRC/frc-server/frc-server.jar /home/franco/FRC/frc-server/frc-server_bkp.jar
curl -L -o /home/franco/FRC/frc-server/frc-server.jar https://github.com/GabFrank/franco-system-backend-filial/releases/download/v3.0.7-1/frc-server.jar
sudo systemctl restart frc && journalctl -f -u frc

#### Volver a la version anterior
rm /home/franco/FRC/frc-server/frc-server.jar
cp /home/franco/FRC/frc-server/frc-server_bkp.jar /home/franco/FRC/frc-server/frc-server.jar


### reiniciar el servidor y ver los logs
sudo systemctl restart frc && journalctl -f -u frc

### Actualizar app
rm /home/franco/FRC/FRC.AppImage
curl -L -o /home/franco/FRC/FRC.AppImage https://github.com/GabFrank/frc-sistemas-integrados-angular/releases/download/3.0.7-1/FRC.AppImage
chmod a+x /home/franco/FRC/FRC.AppImage

### Ver la lista de impresoras, sirve para ver el ip de la caja auxiliar
lpstat -v
