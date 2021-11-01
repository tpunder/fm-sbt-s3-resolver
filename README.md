# Frugal Mechanic SBT S3 Resolver

[![Build Status](https://travis-ci.org/frugalmechanic/fm-sbt-s3-resolver.svg?branch=master)](https://travis-ci.org/frugalmechanic/fm-sbt-s3-resolver)

This SBT plugin adds support for using Amazon S3 for resolving and publishing using s3:// urls.

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [SBT 1.3 Support](#sbt-13-support)
- [SBT 1.1 Support](#sbt-11-support)
- [SBT 1.0 Support](#sbt-10-support)
- [Examples](#examples)
  - [Resolving Dependencies via S3](#resolving-dependencies-via-s3)
  - [Publishing to S3](#publishing-to-s3)
  - [Valid s3:// URL Formats](#valid-s3-url-formats)
- [Usage](#usage)
  - [Add this to your project/plugins.sbt file:](#add-this-to-your-projectpluginssbt-file)
  - [S3 Credentials](#s3-credentials)
    - [Bucket Specific Environment Variables](#bucket-specific-environment-variables)
    - [Bucket Specific Java System Properties](#bucket-specific-java-system-properties)
    - [Bucket Specific Property Files](#bucket-specific-property-files)
    - [Environment Variables](#environment-variables)
    - [Java System Properties](#java-system-properties)
    - [Property File](#property-file)
  - [Custom S3 Credentials](#custom-s3-credentials)
- [IAM Policy Examples](#iam-policy-examples)
  - [Read/Write Policy (for publishing)](#readwrite-policy-for-publishing)
  - [Read-Only Policy](#read-only-policy)
  - [Releases Read-Only, Snapshots Read/Write](#releases-read-only-snapshots-readwrite)
- [IAM Role Policy Examples](#iam-role-policy-examples)
- [S3 Server-Side Encryption](#s3-server-side-encryption)
- [Authors](#authors)
- [Copyright](#copyright)
- [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## SBT 1.3 Support

SBT 1.3 support is available using version `>= 0.19.0`:

```scala
addSbtPlugin("com.frugalmechanic" % "fm-sbt-s3-resolver" % "0.20.0")
```

## SBT 1.1 Support

SBT 1.1 support is available using version `>= 0.14.0`:

## SBT 1.0 Support

Note: **You need to use at least SBT 1.0.4** for this plugin to work with SBT 1.0 due to 
https://github.com/sbt/librarymanagement/issues/175 which was fixed in this pull 
request: https://github.com/sbt/librarymanagement/pull/183

## Examples

### Resolving Dependencies via S3

Maven Style:

```scala
resolvers += "FrugalMechanic Snapshots" at "s3://maven.frugalmechanic.com/snapshots"
```

Ivy Style:

```scala
resolvers += Resolver.url("FrugalMechanic Snapshots", url("s3://maven.frugalmechanic.com/snapshots"))(Resolver.ivyStylePatterns)
```

### Publishing to S3

Maven Style:

```scala
publishMavenStyle := true
publishTo := Some("FrugalMechanic Snapshots" at "s3://maven.frugalmechanic.com/snapshots")
```

Ivy Style:

```scala
publishMavenStyle := false
publishTo := Some(Resolver.url("FrugalMechanic Snapshots", url("s3://maven.frugalmechanic.com/snapshots"))(Resolver.ivyStylePatterns))
```

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

```scala
addSbtPlugin("com.frugalmechanic" % "fm-sbt-s3-resolver" % "0.20.0")
```

### S3 Credentials

S3 Credentials are checked **in the following places and _order_** (e.g. bucket specific settings (\~/.sbt/.&lt;bucket_name&gt;_s3credentials) get resolved before global settings (\~/.sbt/.s3credentials)):

**Note: I think this logic has changed a little bit.  See the S3URLHandler.defaultCredentialsProviderChain for the current implementation: https://github.com/frugalmechanic/fm-sbt-s3-resolver/blob/master/src/main/scala/fm/sbt/S3URLHandler.scala#L166**

#### Bucket Specific Environment Variables

    AWS_ACCESS_KEY_ID_<BUCKET_NAME> -or- <BUCKET_NAME>_AWS_ACCESS_KEY_ID
    AWS_SECRET_KEY_<BUCKET_NAME> -or- <BUCKET_NAME>_AWS_SECRET_KEY
    
**NOTE** - The following transforms are applied to the bucket name before looking up the environment variable:

1. The name is upper-cased
2. Dots (.) and dashes (-) are replaced with an underscore (_)
3. Everything other than A-Z, 0-9, and underscores are removed.
  
Example:

The bucket name "maven.frugalmechanic.com" becomes "MAVEN\_FRUGALMECHANIC\_COM":

```shell
AWS_ACCESS_KEY_ID_MAVEN_FRUGALMECHANIC_COM="XXXXXX" AWS_SECRET_KEY_MAVEN_FRUGALMECHANIC_COM="XXXXXX" sbt
```

#### Bucket Specific Java System Properties

```shell
-Daws.accessKeyId.<bucket_name>=XXXXXX -Daws.secretKey.<bucket_name>=XXXXXX
-D<bucket_name>.aws.accessKeyId=XXXXXX -D<bucket_name>.aws.secretKey=XXXXXX
```
    
Example:

```shell
SBT_OPTS="-Daws.accessKeyId.maven.frugalmechanic.com=XXXXXX -Daws.secretKey.maven.frugalmechanic.com=XXXXXX" sbt
```

#### Bucket Specific Property Files

```shell
~/.sbt/.<bucket_name>_s3credentials
~/.sbt/.s3credentials_<bucket_name>
```

#### Environment Variables

    AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY)
    AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY)
    AWS_ROLE_ARN

Example:

```shell
// Basic Credentials
AWS_ACCESS_KEY_ID="XXXXXX" AWS_SECRET_KEY="XXXXXX" sbt

// IAM Role Credentials
AWS_ACCESS_KEY_ID="XXXXXX" AWS_SECRET_KEY="XXXXXX" AWS_ROLE_ARN="arn:aws:iam::123456789012:role/RoleName" sbt
```

#### Java System Properties

    // Basic Credentials
    -Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX 

    // IAM Role
    -Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX -Daws.arnRole=arn:aws:iam::123456789012:role/RoleName


Example:
 
```shell
// Basic Credentials
SBT_OPTS="-Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX" sbt

// IAM Role Credentials
SBT_OPTS="-Daws.accessKeyId=XXXXXX -Daws.secretKey=XXXXXX -Daws.arnRole=arn:aws:iam::123456789012:role/RoleName" sbt
```

#### Property File

```shell  
~/.sbt/.s3credentials
```
    
The property files should have the following format:
  
```ini
accessKey = XXXXXXXXXX
secretKey = XXXXXXXXXX
// Optional IAM Role
roleArn = arn:aws:iam::123456789012:role/RoleName
```

### Custom S3 Credentials

If the default credential providers do not work for you then you can specify your own AWSCredentialsProvider using the `s3CredentialsProvider` SettingKey in your `build.sbt` file:

```scala
import com.amazonaws.auth.{AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain}
import com.amazonaws.auth.profile.ProfileCredentialsProvider

s3CredentialsProvider := { (bucket: String) =>
  new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("my_profile"),
    DefaultAWSCredentialsProviderChain.getInstance()
  )
}
```

If you are really lazy and want to provide static credentials using this in your `build.sbt` file will work:

```scala
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicAWSCredentials}

s3CredentialsProvider := { (bucket: String) =>
  new AWSStaticCredentialsProvider(new BasicAWSCredentials("your_accessKey", "your_secretKey"))
}
```

## IAM Policy Examples

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

## IAM Role Policy Examples

This is a simple example where a Host AWS Account, can create a Role with permissions for a Client AWS Account to access the Host maven bucket.

  1. Host AWS Account, creates an IAM Role named "ClientAccessRole" with policy:
<pre>
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::<b>[Client AWS Account Id]</b>:user/<b>[Client User Name]</b>"
        },
        "Action": "sts:AssumeRole"
    }
  ]
}
</pre>
  2. Associate the proper [IAM Policy Examples](#iam) to the Host Role
  3. Client AWS Account needs to create an AWS IAM User [Client User Name] and associated a policy to gives it permissions to AssumeRole from the Host AWS Account:
<pre>
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "sts:AssumeRole",
      "Resource": "arn:aws:iam::<b>[Host AWS Account Id]</b>:role/ClientAccessRole"
    }
  ]
}
</pre>

## S3 Server-Side Encryption
S3 supports <a href="http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingServerSideEncryption.html">server side encryption</a>.
The plugin will automatically detect if it needs to ask S3 to use SSE, based on the policies you have on your bucket. If
your bucket denies `PutObject` requests that aren't using SSE, the plugin will include the SSE header in future requests.

To make use of SSE, configure your bucket to enforce the SSE header for `PutObject` requests.

Example:
<pre>
{
  "Version": "2012-10-17",
  "Id": "PutObjPolicy",
  "Statement": [
    {
      "Sid": "DenyIncorrectEncryptionHeader",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<b>YOUR_BUCKET_HERE</b>/*",
      "Condition": {
        "StringNotEquals": {
          "s3:x-amz-server-side-encryption": "AES256"
        }
      }
    },
    {
      "Sid": "DenyUnEncryptedObjectUploads",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:PutObject",
      "Resource": "arn:aws:s3:::<b>YOUR_BUCKET_HERE</b>/*",
      "Condition": {
        "Null": {
          "s3:x-amz-server-side-encryption": "true"
        }
      }
    }
  ]
}
</pre>

## Authors

Tim Underwood (<a href="https://github.com/tpunder" rel="author">GitHub</a>, <a href="https://www.linkedin.com/in/tpunder" rel="author">LinkedIn</a>, <a href="https://twitter.com/tpunder" rel="author">Twitter</a>, <a href="https://plus.google.com/+TimUnderwood0" rel="author">Google Plus</a>)

## Copyright

Copyright [Frugal Mechanic](http://frugalmechanic.com)

## License

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
