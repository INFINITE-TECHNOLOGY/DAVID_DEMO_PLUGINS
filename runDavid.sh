java \
 -Dserver.port=$PORT \
 -Dloader.path="build/libs" \
 -Dspring.config.location="build/conf/application.properties" \
 $JAVA_OPTS \
 -cp "build/libs/david-app-1.0.0.jar" \
 org.springframework.boot.loader.PropertiesLauncher