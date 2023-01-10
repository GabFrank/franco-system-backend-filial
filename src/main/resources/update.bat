net stop frc-server
del C:\FRC\frc-server\frc-server.jar
del C:\FRC\frc-server\WinSW.NET4.out
xcopy C:\FRC\update\frc-server.jar C:\FRC\frc-server\
net start frc-server