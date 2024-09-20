To run kubernetes tests:

Make sure you have deployed greeting, farewell, and meeting services
The .yml files for that are in their perspective projects

```shell
mvn -Dno-build-cache -Dk8sit=true -Dsurefire.skipAfterFailureCount=1 clean package
```