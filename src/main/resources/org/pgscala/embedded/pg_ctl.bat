@echo off
"%~dp0pgsql\bin\pg_ctl.exe" "-D%~dp0data" %1
