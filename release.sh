rm -rf target/release
mkdir target/release
cd target/release
git clone git@github.com:burtbeckwith/grails-dynamic-controller.git
cd grails-dynamic-controller
grails clean
grails compile

#grails publish-plugin --snapshot --stacktrace
grails publish-plugin --stacktrace
