# Frugal Mechanic SBT S3 Resolver

[![Build Status](https://travis-ci.org/frugalmechanic/fm-sbt-s3-resolver.svg?branch=master)](https://travis-ci.org/frugalmechanic/fm-sbt-s3-resolver)

This SBT plugin adds support for using Amazon S3 for resolving and publishing using s3:// urls.

## Table of Contents

  * [Example](#example)
  * [Usage](#usage)
  * [IAM Policy Examples](#iam)
  * [IAM Role Examples](#iam-role)
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

    addSbtPlugin("com.frugalmechanic" % "fm-sbt-s3-resolver" % "0.9.0")

### S3 Credentials

S3 Credentials are checked **in the following places and _order_** (e.g. bucket specific settings (~/.sbt/.&lt;bucket_name&gt;_s3credentials) get resolved before global settings (~/.sbt/.s3credentials)):

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
    AWS_ROLE_ARN

Example:

    // Basic Credentials
    AWS_ACCESS_KEY_ID="XXXXXX" AWS_SECRET_KEY="XXXXXX" sbt

    // IAM Role Credentials
    AWS_ACCESS_KEY_ID="XXXXXX" AWS_SECRET_KEY="XXXXXX" AWS_ROLE_ARN="arn:aws:iam::123456789012:role/RoleName" sbt

#### Java System Properties

    // Basic Credentials
    -Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX 

    // IAM Role
    -Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX -Daws.arnRole=arn:aws:iam::123456789012:role/RoleName


Example:
 
    // Basic Credentials
    SBT_OPTS="-Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX" sbt

    // IAM Role Credentials
    SBT_OPTS="-Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX -Daws.arnRole=arn:aws:iam::123456789012:role/RoleName" sbt

#### Property File
  
    ~/.sbt/.s3credentials
    
The property files should have the following format:
  
    accessKey = XXXXXXXXXX
    secretKey = XXXXXXXXXX
    // Optional IAM Role
    roleArn = arn:aws:iam::123456789012:role/RoleName

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

## <a name="iam-role"></a>IAM Role Policy Examples

This is a simple example where a Host AWS Account, can create a Role with permissions for a Client AWS Account to access the Host maven bucket.

  1. Host AWS Account, creates an IAM Role named "ClientAccessRole" with policy:
<pre>
{
&nbsp;&nbsp;"Version": "2012-10-17",
&nbsp;&nbsp;"Statement": [
&nbsp;&nbsp;  {
&nbsp;&nbsp;    "Effect": "Allow",
&nbsp;&nbsp;    "Principal": {
&nbsp;&nbsp;      "AWS": "arn:aws:iam::[Client AWS Account Id]:user/[Client User Name]"
&nbsp;&nbsp;      },
&nbsp;&nbsp;      "Action": "sts:AssumeRole"
&nbsp;&nbsp;  }
&nbsp;&nbsp;]
}
</pre>
  2. Associate the proper [IAM Policy Examples](#iam) to the Host Role
  3. Client AWS Account needs to create an AWS IAM User [Client User Name] and associated a policy to gives it permissions to AssumeRole from the Host AWS Account:
<pre>
{
&nbsp;&nbsp;"Version": "2012-10-17",
&nbsp;&nbsp;"Statement": [
&nbsp;&nbsp;  {
&nbsp;&nbsp;    "Effect": "Allow",
&nbsp;&nbsp;    "Action": "sts:AssumeRole",
&nbsp;&nbsp;    "Resource": "arn:aws:iam::[Host AWS Account Id]:role/ClientAccessRole"
&nbsp;&nbsp;  }
&nbsp;&nbsp;]
}
</pre>

## <a name="authors"></a>Authors

Tim Underwood (<a href="https://github.com/tpunder" rel="author">GitHub</a>, <a href="https://www.linkedin.com/in/tpunder" rel="author">LinkedIn</a>, <a href="https://twitter.com/tpunder" rel="author">Twitter</a>, <a href="https://plus.google.com/+TimUnderwood0" rel="author">Google Plus</a>)

## <a name="copyright"></a>Copyright

Copyright [Frugal Mechanic](http://frugalmechanic.com)

## <a name="license"></a>License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)