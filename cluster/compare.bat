@echo off
call setfiles.bat

echo ******  %a%
diff %br%\%a% %tr%\%a%
echo ******  %b%
diff %br%\%b% %tr%\%b%
pause
echo ******  %c%
diff %br%\%c% %tr%\%c%
echo ******  %d%
diff %br%\%d% %tr%\%d%
echo ******  %e%
diff %br%\%e% %tr%\%e%
echo ******  %f%
diff %br%\%f% %tr%\%f%
echo ******  %g%
diff %br%\%g% %tr%\%g%
echo ******  %h%
diff %br%\%h% %tr%\%h%
echo ******  %i%
diff %br%\%i% %tr%\%i%

rem echo ******  %j%
rem diff %br%\%j% %tr%\%j%
rem echo ******  %k%
rem diff %br%\%k% %tr%\%k%
rem echo ******  %l%
rem diff %br%\%l% %tr%\%l%
