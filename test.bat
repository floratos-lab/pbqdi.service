REM windows script to test new change on my PC
call C:\apache-tomcat-7.0.41\bin\shutdown.bat
echo on
call mvn package -DskipTests
echo on
if %errorlevel% neq 0 exit /b
rmdir /s /q C:\apache-tomcat-7.0.41\webapps\pbqdi
copy target\pbqdi-0.0.1-SNAPSHOT.war C:\apache-tomcat-7.0.41\webapps\pbqdi.war
if %errorlevel% neq 0 exit /b
call C:\apache-tomcat-7.0.41\bin\startup.bat
echo on
call mvn test
curl --header "content-type: text/xml" -d @example_request.xml http://localhost:8080/pbqdi