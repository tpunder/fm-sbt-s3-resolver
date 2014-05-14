# Frugal Mechanic SBT S3 Resolver

[![Build Status](https://travis-ci.org/frugalmechanic/fm-sbt-s3-resolver.svg?branch=master)](https://travis-ci.org/frugalmechanic/fm-sbt-s3-resolver)

This SBT plugin adds support for using Amazon S3 for resolving and publishing using s3:// urls.

## Example

### Resolving Dependencies via S3

    resolvers += "FrugalMechanic Snapshots" at "s3://maven.frugalmechanic.com/snapshots"

### Publishing to S3

    publishTo := Some("FrugalMechanic Snapshots" at "s3://maven.frugalmechanic.com/snapshots")

### Valid s3:// URL Formats

The examples above are using the [Static Website Using a Custom Domain](http://docs.aws.amazon.com/AmazonS3/latest/dev/website-hosting-custom-domain-walkthrough.html) functionality of S3.

These would also be equivalent (for the **maven.frugalmechanic.com** bucket):

    s3://s3-us-west-2.amazonaws.com/maven.frugalmechanic.com/snapshots
    s3://maven.frugalmechanic.com.s3-us-west-2.amazonaws.com/snapshots
    s3://maven.frugalmechanic.com.s3.amazonaws.com/snapshots
    s3://s3.amazonaws.com/maven.frugalmechanic.com/snapshots

All of these forms should work:

    s3://[BUCKET]/[OPTIONAL_PATH]
    s3://s3.amazonaws.com/[BUCKET]/[OPTIONAL_PATH]
    s3://[BUCKET].s3.amazonaws.com/[OPTIONAL_PATH]
    s3://s3-[REGION].amazonaws.com/[BUCKET]/[OPTIONAL_PATH]
    s3://[BUCKET].s3-[REGION].amazonaws.com/[OPTIONAL_PATH]

## Usage

### Add this to your project/plugins.sbt file:

    addSbtPlugin("com.frugalmechanic" % "fm-sbt-s3-resolver" % "0.2.0")

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

## Authors

Tim Underwood (<a href="https://github.com/tpunder" rel="author">GitHub</a>, <a href="https://www.linkedin.com/in/tpunder" rel="author">LinkedIn</a>, <a href="https://twitter.com/tpunder" rel="author">Twitter</a>, <a href="https://plus.google.com/+TimUnderwood0" rel="author">Google Plus</a>)

## Copyright

Copyright [Frugal Mechanic](http://frugalmechanic.com)

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)