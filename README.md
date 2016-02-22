# Frugal Mechanic SBT S3 Resolver

[![Build Status](https://travis-ci.org/frugalmechanic/fm-sbt-s3-resolver.svg?branch=master)](https://travis-ci.org/frugalmechanic/fm-sbt-s3-resolver)

This SBT plugin adds support for using Amazon S3 for resolving and publishing using s3:// urls.

## Table of Contents

  * [Example](#example)
  * [Usage](#usage)
  * [IAM Policy Examples](#iam)
  * [Authors](#authors)
  * [Copyright](#copyright)
  * [License](#license)

## <a name="example"></a>Example

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

## <a name="usage"></a>Usage

### Add this to your project/plugins.sbt file:

    addSbtPlugin("com.frugalmechanic" % "fm-sbt-s3-resolver" % "0.8.0")

### S3 Credentials

S3 Credentials are checked in the following places and order:

#### Bucket Specific Environment Variables

    AWS_ACCESS_KEY_ID_<BUCKET_NAME> -or- <BUCKET_NAME>_AWS_ACCESS_KEY_ID
    AWS_SECRET_KEY_<BUCKET_NAME> -or- <BUCKET_NAME>_AWS_SECRET_KEY
    
**NOTE** - The following transforms are applied to the bucket name before looking up the environment variable:

1. The name is upper-cased
2. Dots (.) and dashes (-) are replaced with an underscore (_)
3. Everything other than A-Z, 0-9, and underscores are removed.
  
Example:

The bucket name "maven.frugalmechanic.com" becomes "MAVEN\_FRUGALMECHANIC\_COM":

    AWS_ACCESS_KEY_ID_MAVEN_FRUGALMECHANIC_COM="XXXXXX" AWS_SECRET_KEY_MAVEN_FRUGALMECHANIC_COM="XXXXXX" sbt

#### Bucket Specific Java System Properties

    -Daws.accessKeyId.<bucket_name>=XXXXXX -Daws.secretKey.<bucket_name>=XXXXXX
    -D<bucket_name>.aws.accessKeyId=XXXXXX -D<bucket_name>.aws.secretKey=XXXXXX
    
Example:

    SBT_OPTS="-Daws.accessKeyId.maven.frugalmechanic.com=XXXXXX -Daws.secretKey.maven.frugalmechanic.com=XXXXXX" sbt

#### Bucket Specific Property Files

    ~/.sbt/.<bucket_name>_s3credentials
    ~/.sbt/.s3credentials_<bucket_name>

#### Environment Variables

    AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY)
    AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY)

Example:

    AWS_ACCESS_KEY_ID="XXXXXX" AWS_SECRET_KEY="XXXXXX" sbt
    
#### Java System Properties

    -Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX 

Example:

    SBT_OPTS="-Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX" sbt

#### Property File
  
    ~/.sbt/.s3credentials
    
The property files should have the following format:
  
    accessKey = XXXXXXXXXX
    secretKey = XXXXXXXXXX

## <a name="iam"></a>IAM Policy Examples

I recommend that you create IAM Credentials for reading/writing your Maven S3 Bucket.  Here are some examples for our **maven.frugalmechanic.com** bucket:

### Read/Write Policy (for publishing)

<pre>
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation"],
      "Resource": "arn:aws:s3:::*"
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::<b>maven.frugalmechanic.com</b>"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:DeleteObject","s3:GetObject","s3:PutObject"],
      "Resource": ["arn:aws:s3:::<b>maven.frugalmechanic.com</b>/*"]
    }
  ]
}
</pre>

### Read-Only Policy

<pre>
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation"],
      "Resource": "arn:aws:s3:::*"
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::<b>maven.frugalmechanic.com</b>"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::<b>maven.frugalmechanic.com</b>/*"]
    }
  ]
}
</pre>

### Releases Read-Only, Snapshots Read/Write

<pre>
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation"],
      "Resource": "arn:aws:s3:::*"
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::<b>maven.frugalmechanic.com</b>"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::<b>maven.frugalmechanic.com</b>/<b>releases</b>/*"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:DeleteObject","s3:GetObject","s3:PutObject"],
      "Resource": ["arn:aws:s3:::<b>maven.frugalmechanic.com</b>/<b>snapshots</b>/*"]
    }
  ]
}
</pre>

## <a name="authors"></a>Authors

Tim Underwood (<a href="https://github.com/tpunder" rel="author">GitHub</a>, <a href="https://www.linkedin.com/in/tpunder" rel="author">LinkedIn</a>, <a href="https://twitter.com/tpunder" rel="author">Twitter</a>, <a href="https://plus.google.com/+TimUnderwood0" rel="author">Google Plus</a>)

## <a name="copyright"></a>Copyright

Copyright [Frugal Mechanic](http://frugalmechanic.com)

## <a name="license"></a>License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)