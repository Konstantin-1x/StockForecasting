@echo off
setlocal

set PGPASSWORD=admin
set PSQL=C:\Program Files\PostgreSQL\18\bin\psql.exe
set SQL_FILE=C:\Users\Work\Desktop\stockforecasting-backup.sql
set OUT_LOG=E:\ProjectJava\StockForecasting\import-stockforecasting.out.log
set ERR_LOG=E:\ProjectJava\StockForecasting\import-stockforecasting.err.log
set STATUS_FILE=E:\ProjectJava\StockForecasting\import-stockforecasting.status.txt

echo Import started at %DATE% %TIME% > "%STATUS_FILE%"
"%PSQL%" -h localhost -U postgres -d stockforecasting_imported -v ON_ERROR_STOP=1 -f "%SQL_FILE%" > "%OUT_LOG%" 2> "%ERR_LOG%"
set EXIT_CODE=%ERRORLEVEL%
echo Import finished at %DATE% %TIME% with exit code %EXIT_CODE% >> "%STATUS_FILE%"
exit /b %EXIT_CODE%
