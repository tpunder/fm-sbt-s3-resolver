This SBT plugin adds support for using Amazon S3 for resolving and publishing using s3:// urls.

# Example

### Resolving Dependencies via S3

    resolvers += "FrugalMechanic Snapshots" at "s3://maven.frugalmechanic.com/snapshots"

### Publishing to S3

    publishTo := Some("FrugalMechanic Snapshots" at "s3://maven.frugalmechanic.com/snapshots")


# Usage

### Add this to your project/plugins.sbt file:

    addSbtPlugin("com.frugalmechanic" % "fm-sbt-s3-resolver" % "0.1.0")

### S3 Credentials

S3 Credentials are checked in the following places and order:

#### Environment Variables

    AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY)
    AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY)

#### Java System Properties

    -Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX 

#### Property Files:
  
    ~/.sbt/.<bucket_name>_s3credentials
    ~/.sbt/.s3credentials
    
The property files should have the following format:
  
    accessKey = XXXXXXXXXX
    secretKey = XXXXXXXXXX

# License

Licensed under the Apache License, Version 2.0.  See the LICENSE file for details.