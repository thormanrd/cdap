.. meta::
    :author: Cask Data, Inc.
    :description: HTTP RESTful Interface to the Cask Data Application Platform
    :copyright: Copyright © 2014 Cask Data, Inc.

.. _http-restful-api-dataset:

===========================================================
Dataset HTTP RESTful API
===========================================================

.. highlight:: console

The Dataset API allows you to interact with Datasets through HTTP. You can list, create,
delete, and truncate Datasets. For details, see the
:ref:`CDAP Components, Datasets section <datasets-index>`


Listing all Datasets
--------------------

You can list all Datasets in CDAP by issuing an HTTP GET request to the URL::

  GET <base-url>/namespaces/<namespace>/data/datasets

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID

The response body will contain a JSON-formatted list of the existing Datasets::

  {
     "name":"cdap.user.purchases",
     "type":"co.cask.cdap.api.dataset.lib.ObjectStore",
     "properties":{
        "schema":"...",
        "type":"..."
     },
     "datasetSpecs":{
        ...
     }
   }

.. _http-restful-api-dataset-creating:

Creating a Dataset
------------------

You can create a Dataset by issuing an HTTP PUT request to the URL::

  PUT <base-url>/namespaces/<namespace>/data/datasets/<dataset-name>

with JSON-formatted name of the dataset type and properties in a body::

  {
     "typeName":"<type-name>",
     "properties":{<properties>}
  }


.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID
   * - ``<dataset-name>``
     - Name of the new Dataset
   * - ``<type-name>``
     - Type of the new Dataset
   * - ``<properties>``
     - Dataset properties, map of String to String.

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Requested Dataset was successfully created
   * - ``404 Not Found``
     - Requested Dataset type was not found
   * - ``409 Conflict``
     - Dataset with the same name already exists

.. rubric:: Example
.. list-table::
   :widths: 20 80
   :stub-columns: 1

   * - HTTP Request
     - ``PUT <base-url>/namespaces/default/data/datasets/mydataset``
   * - Body
     - ``{"typeName":"co.cask.cdap.api.dataset.table.Table",`` ``"properties":{"dataset.table.ttl":"3600"}}``
   * - Description
     - Creates a Dataset named *mydataset* of the type ``Table`` in the namespace *default*
       with the time-to-live property set to 1 hour

.. _http-restful-api-dataset-updating:

Updating an Existing Dataset
----------------------------

You can update an existing dataset's table and properties by issuing an HTTP PUT request to the URL::

	PUT <base-url>/namespaces/<namespace>/data/datasets/<dataset-name>/properties

with JSON-formatted name of the dataset type and properties in the body::

  {
     "key1":"value1",
     "key2":"value2"
  }

**Note:** The Dataset must exist, and the instance and type passed must match with the existing Dataset.

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID
   * - ``<dataset-name>``
     - Name of the existing Dataset
   * - ``<type-name>``
     - Type of the existing Dataset
   * - ``<properties>``
     - Dataset properties as a map of String to String

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Requested Dataset was successfully updated
   * - ``404 Not Found``
     - Requested Dataset instance was not found

.. rubric:: Example
.. list-table::
   :widths: 20 80
   :stub-columns: 1

   * - HTTP Request
     - ``PUT <base-url>/namespaces/default/data/datasets/mydataset/properties``
   * - Body
     - ``{"dataset.table.ttl":"7200"}``
   * - Description
     - For the *mydataset* of type ``Table`` of the namespace *default*, update the Dataset
       and its time-to-live property to 2 hours


Deleting a Dataset
------------------

You can delete a Dataset by issuing an HTTP DELETE request to the URL::

  DELETE <base-url>/namespaces/<namespace>/data/datasets/<dataset-name>

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID
   * - ``<dataset-name>``
     - Dataset name

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Dataset was successfully deleted
   * - ``404 Not Found``
     - Dataset named *dataset-name* could not be found

.. rubric:: Example
.. list-table::
   :widths: 20 80
   :stub-columns: 1

   * - HTTP Request
     - ``DELETE <base-url>/namespaces/default/data/datasets/mydataset``
   * - Description
     - Deletes the Dataset *mydataset* in the namespace *default*


Deleting all Datasets
---------------------

If the property ``enable.unrecoverable.reset`` in ``cdap-site.xml`` is set to ``true``, you can delete all Datasets
by issuing an HTTP DELETE request to the URL::

  DELETE <base-url>/namespaces/<namespace>/unrecoverable/data/datasets

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - All Datasets were successfully deleted


If the property ``enable.unrecoverable.reset`` in ``cdap-site.xml`` is not set to
``true``, this operation will return a Status Code ``403 Forbidden``. Note that this
operation can only be performed if all programs are stopped. If there's at least one
program that is running, this operation will return a Status Code ``400 Bad Request``.

Truncating a Dataset
--------------------

You can truncate a Dataset by issuing an HTTP POST request to the URL::

  POST <base-url>/namespaces/<namespace>/data/datasets/<dataset-name>/admin/truncate

This will clear the existing data from the Dataset. This cannot be undone.

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID
   * - ``<dataset-name>``
     - Dataset name

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Dataset was successfully truncated

Datasets used by an Application
-------------------------------

You can retrieve a list of Datasets used by an Application by issuing a HTTP GET request to the URL::

  GET <base-url>/namespaces/<namespace>/apps/<app-id>/datasets

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID
   * - ``<app-id>``
     - Application ID

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Request was successful

Datasets used by a Program
--------------------------

You can retrieve a list of Datasets used by a program by issuing a HTTP GET request to the URL::

  GET <base-url>/namespaces/<namespace>/apps/<app-id>/<program-type>/<program-id>/datasets

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID
   * - ``<app-id>``
     - Application ID
   * - ``<program-type>``
     - Program Type, one of ``flows``, ``mapreduce``, ``services``, ``spark``, or ``workflows``
   * - ``<program-id>``
     - Program ID

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Request was successful

Datasets used by an Adapter
---------------------------

You can retrieve a list of Datasets used by an Adapter by issuing a HTTP GET request to the URL::

  GET <base-url>/namespaces/<namespace>/adapters/<adapter-id>/datasets

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID
   * - ``<adapter-id>``
     - Adapter ID

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Request was successful

Programs using a Dataset
------------------------

You can retrieve a list of programs that are using a Dataset by issuing a HTTP GET request to the URL::

  GET <base-url>/namespaces/<namespace>/data/datasets/<dataset-id>/programs

.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Parameter
     - Description
   * - ``<namespace>``
     - Namespace ID
   * - ``<dataset-id>``
     - Dataset ID

.. rubric:: HTTP Responses
.. list-table::
   :widths: 20 80
   :header-rows: 1

   * - Status Codes
     - Description
   * - ``200 OK``
     - Request was successful
