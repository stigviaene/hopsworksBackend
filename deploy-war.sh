#!/bin/bash
set -e
mvn clean install
scp target/hop-dashboard.war glassfish@snurran.sics.se:/var/www/hops
#cp target/hop-dashboard.war /var/www/
