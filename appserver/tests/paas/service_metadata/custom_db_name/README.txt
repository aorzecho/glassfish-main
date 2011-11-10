This test verifies the creation of a custom database when database name is specified in the glassfish-services.xml file. 

Steps to run this test:
-----------------------

1. Unzip latest version of glassfish.zip and set S1AS_HOME enviroment variable to point to the extracted GlassFish location.

  For example: export S1AS_HOME=/tmp/glassfish3/glassfish

2. Setup virtualization enviroment for your GlassFish installation. 

   For example, modify native_setup.sh to suite your system details and run it.

3. Restart domain after this step.

4. Compile this test : mvn clean compile war:war

5. Deploy the war : asadmin deploy /tmp/custom_db_name_paas_sample.war

The tables listed in the initsql.sql file are created and loaded with data in the new database, as is evident from the output of the Servlet.
