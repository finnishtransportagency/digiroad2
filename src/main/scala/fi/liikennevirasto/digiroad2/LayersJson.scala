package fi.liikennevirasto.digiroad2

import fi.liikennevirasto.digiroad2.user.Configuration

object LayersJson {
  def layers(userConfig: Configuration): String = {
   val municipalityNumber = userConfig.municipalityNumber.getOrElse(235)
   val municipalitiesOfUserParams =
     userConfig.authorizedMunicipalities.map(id => "municipalityNumber=" + id).mkString("&")


    s"""{
  "layers": [{
    "wmsName": "bussit",
    "type": "busstoplayer",
    "id": 236,
    "minScale": 5000,
    "wmsUrl": "/data/dummy/busstops.json",
    "url": "/api/assets?assetTypeId=10&${municipalitiesOfUserParams}&validityPeriod=future",
    "roadLinesUrl": "/api/roadlinks?municipalityNumber=${municipalityNumber}&validityPeriod=future",
    "maxScale": 1,
    "orgName": "LiVi",
    "inspire": "Ominaisuustiedot",
    "name": "Tulevat",
    "opacity": 30
  }, {
    "wmsName": "bussit",
    "type": "busstoplayer",
    "id": 237,
    "minScale": 5000,
    "wmsUrl": "/data/dummy/busstops.json",
    "url": "/api/assets?assetTypeId=10&municipalityNumber=${municipalitiesOfUserParams}&validityPeriod=past",
    "roadLinesUrl": "/api/roadlinks?municipalityNumber=${municipalityNumber}&validityPeriod=past",
    "maxScale": 1,
    "orgName": "LiVi",
    "inspire": "Ominaisuustiedot",
    "name": "Käytöstä poistuneet",
    "opacity": 30
  }, {
    "dataUrl_uuid": "b20a360b-1734-41e5-a5b8-0e90dd9f2af3",
    "wmsName": "ortokuva",
    "styles": [{
      "isDefault": true,
      "identifier": "default"
    }],
    "tileMatrixSetId": "ETRS-TM35FIN",
    "geom": "POLYGON ((51857.07752019336 6590697.596588667, 759905.4330615391 6590697.596588667, 759905.4330615391 7795699.644448195, 51857.07752019336 7795699.644448195, 51857.07752019336 6590697.596588667))",
    "type": "wmtslayer",
    "orgName": "Maanmittauslaitos",
    "baseLayerId": -1,
    "id": 24,
    "minScale": 50000,
    "style": "default",
    "wmsUrl": "maasto/wmts",
    "name": "Ortokuvat",
    "permissions": {
      "publish": "no_publication_permission"
    },
    "subtitle": "(WMTS)",
    "opacity": 100,
    "tileMatrixSetData": {
      "contents": {
        "tileMatrixSets": {
          "ETRS-TM35FIN": {
            "bounds": {
              "bottom": 6291456,
              "left": -548576,
              "right": 1548576,
              "top": 8388608
            },
            "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
            "projection": "urn:ogc:def:crs:EPSG:6.3:3067",
            "matrixIds": [{
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 1,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "0",
              "matrixHeight": 1,
              "scaleDenominator": 2.925714285714286E7
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 2,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "1",
              "matrixHeight": 2,
              "scaleDenominator": 1.462857142857143E7
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 4,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "2",
              "matrixHeight": 4,
              "scaleDenominator": 7314285.714285715
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 8,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "3",
              "matrixHeight": 8,
              "scaleDenominator": 3657142.8571428573
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 16,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "4",
              "matrixHeight": 16,
              "scaleDenominator": 1828571.4285714286
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 32,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "5",
              "matrixHeight": 32,
              "scaleDenominator": 914285.7142857143
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 64,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "6",
              "matrixHeight": 64,
              "scaleDenominator": 457142.85714285716
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 128,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "7",
              "matrixHeight": 128,
              "scaleDenominator": 228571.42857142858
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 256,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "8",
              "matrixHeight": 256,
              "scaleDenominator": 114285.71428571429
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 512,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "9",
              "matrixHeight": 512,
              "scaleDenominator": 57142.857142857145
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 1024,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "10",
              "matrixHeight": 1024,
              "scaleDenominator": 28571.428571428572
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 2048,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "11",
              "matrixHeight": 2048,
              "scaleDenominator": 14285.714285714286
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 4096,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "12",
              "matrixHeight": 4096,
              "scaleDenominator": 7142.857142857143
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 8192,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "13",
              "matrixHeight": 8192,
              "scaleDenominator": 3571.4285714285716
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 16384,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "14",
              "matrixHeight": 16384,
              "scaleDenominator": 1785.7142857142858
            }],
            "identifier": "ETRS-TM35FIN"
          }
        },
        "layers": [{
          "resourceUrls": [{
            "template": "maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
            "format": "image/png",
            "resourceType": "tile"
          }],
          "tileMatrixSetLinks": [{
            "tileMatrixSet": "ETRS-TM35FIN"
          }],
          "title": "Taustakartta",
          "layers": [

          ],
          "dimensions": [

          ],
          "styles": [{
            "isDefault": true,
            "identifier": "default"
          }],
          "resourceUrl": {
            "tile": {
              "template": "maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
              "format": "image/png",
              "resourceType": "tile"
            }
          },
          "identifier": "taustakartta",
          "formats": [
            "image/png"
          ]
        }, {
          "resourceUrls": [{
            "template": "maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
            "format": "image/jpeg",
            "resourceType": "tile"
          }],
          "tileMatrixSetLinks": [{
            "tileMatrixSet": "ETRS-TM35FIN"
          }],
          "title": "Ortokuva",
          "layers": [

          ],
          "dimensions": [

          ],
          "styles": [{
            "isDefault": true,
            "identifier": "default"
          }],
          "resourceUrl": {
            "tile": {
              "template": "maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
              "format": "image/jpeg",
              "resourceType": "tile"
            }
          },
          "identifier": "ortokuva",
          "formats": [
            "image/jpeg"
          ]
        }, {
          "resourceUrls": [{
            "template": "maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
            "format": "image/png",
            "resourceType": "tile"
          }],
          "tileMatrixSetLinks": [{
            "tileMatrixSet": "ETRS-TM35FIN"
          }],
          "title": "Maastokartta",
          "layers": [

          ],
          "dimensions": [

          ],
          "styles": [{
            "isDefault": true,
            "identifier": "default"
          }],
          "resourceUrl": {
            "tile": {
              "template": "maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
              "format": "image/png",
              "resourceType": "tile"
            }
          },
          "identifier": "maastokartta",
          "formats": [
            "image/png"
          ]
        }]
      },
      "operationsMetadata": {
        "GetTile": {
          "dcp": {
            "http": {
              "get": [{
                "constraints": {
                  "GetEncoding": {
                    "allowedValues": {
                      "KVP": true
                    }
                  }
                },
                "url": "maasto/wmts?"
              }]
            }
          }
        },
        "GetCapabilities": {
          "dcp": {
            "http": {
              "get": [{
                "constraints": {
                  "GetEncoding": {
                    "allowedValues": {
                      "KVP": true
                    }
                  }
                },
                "url": "maasto/wmts?"
              }]
            }
          }
        },
        "GetFeatureInfo": {
          "dcp": {
            "http": {
              "get": [{
                "constraints": {
                  "GetEncoding": {
                    "allowedValues": {
                      "KVP": true
                    }
                  }
                },
                "url": "maasto/wmts?"
              }]
            }
          }
        }
      },
      "version": "1.0.0"
    },
    "inspire": "Ortoilmakuvat",
    "maxScale": 1
  }, {
    "dataUrl_uuid": "c22da116-5095-4878-bb04-dd7db3a1a341",
    "wmsName": "maastokartta",
    "styles": [{
      "isDefault": true,
      "identifier": "default"
    }],
    "tileMatrixSetId": "ETRS-TM35FIN",
    "type": "wmtslayer",
    "orgName": "Maanmittauslaitos",
    "baseLayerId": -1,
    "id": "base_2",
    "style": "default",
    "wmsUrl": "maasto/wmts",
    "name": "Maastokartta",
    "permissions": {
      "publish": "no_publication_permission"
    },
    "subtitle": "(WMTS)",
    "opacity": 100,
    "tileMatrixSetData": {
      "contents": {
        "tileMatrixSets": {
          "ETRS-TM35FIN": {
            "bounds": {
              "bottom": 6291456,
              "left": -548576,
              "right": 1548576,
              "top": 8388608
            },
            "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
            "projection": "urn:ogc:def:crs:EPSG:6.3:3067",
            "matrixIds": [{
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 1,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "0",
              "matrixHeight": 1,
              "scaleDenominator": 2.925714285714286E7
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 2,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "1",
              "matrixHeight": 2,
              "scaleDenominator": 1.462857142857143E7
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 4,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "2",
              "matrixHeight": 4,
              "scaleDenominator": 7314285.714285715
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 8,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "3",
              "matrixHeight": 8,
              "scaleDenominator": 3657142.8571428573
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 16,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "4",
              "matrixHeight": 16,
              "scaleDenominator": 1828571.4285714286
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 32,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "5",
              "matrixHeight": 32,
              "scaleDenominator": 914285.7142857143
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 64,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "6",
              "matrixHeight": 64,
              "scaleDenominator": 457142.85714285716
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 128,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "7",
              "matrixHeight": 128,
              "scaleDenominator": 228571.42857142858
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 256,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "8",
              "matrixHeight": 256,
              "scaleDenominator": 114285.71428571429
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 512,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "9",
              "matrixHeight": 512,
              "scaleDenominator": 57142.857142857145
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 1024,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "10",
              "matrixHeight": 1024,
              "scaleDenominator": 28571.428571428572
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 2048,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "11",
              "matrixHeight": 2048,
              "scaleDenominator": 14285.714285714286
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 4096,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "12",
              "matrixHeight": 4096,
              "scaleDenominator": 7142.857142857143
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 8192,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "13",
              "matrixHeight": 8192,
              "scaleDenominator": 3571.4285714285716
            }, {
              "topLeftCorner": {
                "lon": -548576,
                "lat": 8388608
              },
              "supportedCRS": "urn:ogc:def:crs:EPSG:6.3:3067",
              "matrixWidth": 16384,
              "tileHeight": 256,
              "tileWidth": 256,
              "identifier": "14",
              "matrixHeight": 16384,
              "scaleDenominator": 1785.7142857142858
            }],
            "identifier": "ETRS-TM35FIN"
          }
        },
        "layers": [{
          "resourceUrls": [{
            "template": "maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
            "format": "image/png",
            "resourceType": "tile"
          }],
          "tileMatrixSetLinks": [{
            "tileMatrixSet": "ETRS-TM35FIN"
          }],
          "title": "Taustakartta",
          "layers": [

          ],
          "dimensions": [

          ],
          "styles": [{
            "isDefault": true,
            "identifier": "default"
          }],
          "resourceUrl": {
            "tile": {
              "template": "maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
              "format": "image/png",
              "resourceType": "tile"
            }
          },
          "identifier": "taustakartta",
          "formats": [
            "image/png"
          ]
        }, {
          "resourceUrls": [{
            "template": "maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
            "format": "image/jpeg",
            "resourceType": "tile"
          }],
          "tileMatrixSetLinks": [{
            "tileMatrixSet": "ETRS-TM35FIN"
          }],
          "title": "Ortokuva",
          "layers": [

          ],
          "dimensions": [

          ],
          "styles": [{
            "isDefault": true,
            "identifier": "default"
          }],
          "resourceUrl": {
            "tile": {
              "template": "maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
              "format": "image/jpeg",
              "resourceType": "tile"
            }
          },
          "identifier": "ortokuva",
          "formats": [
            "image/jpeg"
          ]
        }, {
          "resourceUrls": [{
            "template": "maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
            "format": "image/png",
            "resourceType": "tile"
          }],
          "tileMatrixSetLinks": [{
            "tileMatrixSet": "ETRS-TM35FIN"
          }],
          "title": "Maastokartta",
          "layers": [

          ],
          "dimensions": [

          ],
          "styles": [{
            "isDefault": true,
            "identifier": "default"
          }],
          "resourceUrl": {
            "tile": {
              "template": "maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
              "format": "image/png",
              "resourceType": "tile"
            }
          },
          "identifier": "maastokartta",
          "formats": [
            "image/png"
          ]
        }]
      },
      "operationsMetadata": {
        "GetTile": {
          "dcp": {
            "http": {
              "get": [{
                "constraints": {
                  "GetEncoding": {
                    "allowedValues": {
                      "KVP": true
                    }
                  }
                },
                "url": "maasto/wmts?"
              }]
            }
          }
        },
        "GetCapabilities": {
          "dcp": {
            "http": {
              "get": [{
                "constraints": {
                  "GetEncoding": {
                    "allowedValues": {
                      "KVP": true
                    }
                  }
                },
                "url": "maasto/wmts?"
              }]
            }
          }
        },
        "GetFeatureInfo": {
          "dcp": {
            "http": {
              "get": [{
                "constraints": {
                  "GetEncoding": {
                    "allowedValues": {
                      "KVP": true
                    }
                  }
                },
                "url": "maasto/wmts?"
              }]
            }
          }
        }
      },
      "version": "1.0.0"
    },
    "inspire": "Maastokartat"
  }]
}"""
  }
}