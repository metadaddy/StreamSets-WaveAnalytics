StreamSets Data Collector Destination for Salesforce Wave Analytics
===================================================================

The StreamSets Wave destination allows you to write data from StreamSets Data Collector (SDC) to Salesforce Wave Analytics (Wave). The destination periodically writes datasets to Wave, and can be configured to combine those datasets into a single aggregation for analysis via a Wave dataflow.

At the time of writing, the Wave destination is in its early stages of development. Feel free to use it, and [report any issues](https://github.com/metadaddy/StreamSets-WaveAnalytics/issues) that you might find.

Pre-requisites
--------------

You will need the following:

* [StreamSets Data Collector](https://streamsets.com/product/)

* A Salesforce Environment ('org') with Wave Analytics enabled. For development and evaluation, the easiest way to get one is via the [Wave Analytics Basics](https://developer.salesforce.com/trailhead/module/wave_analytics_basics) Trailhead module.

Configuration
-------------

| Wave Property | Description |
| --- | --- |
| Username | The username for your Salesforce org. |
| Password | The password for your Salesforce org. For security, we recommend you store the password in a plain text file (no carriage return/newline) in SDC's `resources` directory and reference it as `${runtime:loadResource('wavePassword.txt',true)}` |
| Edgemart Alias | A dataset name. This will be used as a prefix in creating individual datasets, and as the name of the aggregated dataset. The dataset name must be unique across your Salesforce org. |
| Dataset Wait Time (secs) | Maximum time to wait for new data before requesting that the current dataset be processed. This must be at least as long as the **Batch Wait Time** of your pipeline's source, as the Wave destination relies on an empty batch as a signal that there is no data to be processed. |
| Use Dataflow | If enabled, the Wave destination will create and update a Wave dataflow to combine the datasets it writes into a single aggregation |
| Dataflow Name | A dataflow to use for aggregation. This should be empty - i.e. have content `{}` before you start the pipeline. If there is any existing content in the Dataflow, it will be overwritten. |
| Run Dataflow After Upload | If enabled, the Wave destination will request that the dataflow run after each dataset is uploaded. Note that, currently, Salesforce limits each org to 24 dataflow executions per 24 hour period, so you should carefully consider whether you should enable this setting in production. |
| Metadata JSON | Metadata describing the schema of the dataset. This is optional but recommended, as it allows Wave to recognize date and currency fields and show field names. See the [Wave Analytics External Data Format Reference](https://developer.salesforce.com/docs/atlas.en-us.bi_dev_guide_ext_data_format.meta/bi_dev_guide_ext_data_format/) for details on the Metadata JSON syntax. |

Operation
---------

For best results, configure a pipeline with an origin that supports the **Batch Wait Time** configuration property:

* Amazon S3
* Directory
* File Tail
* HTTP Client
* JMS Consumer
* Kafka Consumer
* Kinesis Consumer
* MapR Streams Consumer
* MongoDB
* Omniture
* RabbitMQ Consumer
* SDC RPC
* UDP Source

The Wave destination relies on the source sending an empty batch after the Batch Wait Time, so that it can request that the dataset be processed. Other origins, such as Hadoop FS, should function correctly, but you will need to manually stop the pipeline for the dataset to be processed by Wave.

This video shows the Wave destination in action:

[![YouTube video](http://img.youtube.com/vi/YkyBrmOz5P8/maxresdefault.jpg)](//www.youtube.com/watch?v=YkyBrmOz5P8)