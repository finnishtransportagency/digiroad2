(function(root) {
  root.ConfigurationTestData = {
    generate: function() {
      return {
      "mapfull": {
        "state": {
          "selectedLayers": [{
            "id": "base_35"
          }],
            "srs": "EPSG:3067",
            "zoom": 10,
          "east": "374750.0",
          "north": "6677409.0"
        },
        "conf": {
          "mapOptions": {
            "srsName": "EPSG:3067",
              "maxExtent": {
              "bottom": 6291456,
                "left": -548576,
                "right": 1548576,
                "top": 8388608
            },
            "resolutions": [
              2048,
              1024,
              512,
              256,
              128,
              64,
              32,
              16,
              8,
              4,
              2,
              1,
              0.5
            ]
          },
          "size": {
            "width": "100%",
              "height": "100%"
          },
          "globalMapAjaxUrl": "api/layers?",
            "plugins": [{
            "id": "Oskari.mapframework.bundle.mapmodule.plugin.LayersPlugin"
          }, {
            "id": "Oskari.mapframework.mapmodule.WmsLayerPlugin"
          }, {
            "id": "Oskari.mapframework.mapmodule.MarkersPlugin"
          }, {
            "id": "Oskari.mapframework.mapmodule.ControlsPlugin",
            "config" : {
              "keyboardControls" : false
            }
          }, {
            "id": "Oskari.mapframework.mapmodule.GetInfoPlugin"
          }, {
            "id": "Oskari.digiroad2.bundle.map.Map"
          }, {
            "id": "Oskari.mapframework.wmts.mapmodule.plugin.WmtsLayerPlugin"
          }, {
            "id": "Oskari.mapframework.bundle.mapmodule.plugin.ScaleBarPlugin"
          }, {
            "id": "Oskari.mapframework.bundle.mapmodule.plugin.Portti2Zoombar",
            "config" : {
              "location" : {
                "classes" : "right top"
              }
            }
          }, {
            "id": "Oskari.mapframework.bundle.mapmodule.plugin.BackgroundLayerSelectionPlugin",
            "config": {
              "baseLayers": [
                "base_2",
                "24",
                "base_35"
              ],
              "showAsDropdown": false
            }
          }],
            "layers": [{
            "wmsName": "bussit",
            "type": "map",
            "id": 235,
            "minScale": 6000,
            "roadLinesUrl": "api/roadlinks",
            "maxScale": 1,
            "orgName": "LiVi",
            "inspire": "Ominaisuustiedot",
            "name": "Voimassaolevat"
          }, {
            "dataUrl_uuid": "c22da116-5095-4878-bb04-dd7db3a1a341",
            "wmsName": "taustakartta",
            "styles": [{
              "isDefault": true,
              "identifier": "default"
            }],
            "tileMatrixSetId": "ETRS-TM35FIN",
            "type": "wmtslayer",
            "orgName": "Maanmittauslaitos",
            "baseLayerId": -1,
            "id": "base_35",
            "style": "default",
            "wmsUrl": "../maasto",
            "name": "Taustakarttasarja",
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
                    "template": "../maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                      "template": "../maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                    "template": "../maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
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
                      "template": "../maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
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
                    "template": "../maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                      "template": "../maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                        "url": "../maasto/wmts?"
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
                        "url": "../maasto/wmts?"
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
                        "url": "../maasto/wmts?"
                      }]
                    }
                  }
                }
              },
              "version": "1.0.0"
            },
            "inspire": "Taustakartat"
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
            "wmsUrl": "../maasto/wmts",
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
                    "template": "../maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                      "template": "../maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                    "template": "../maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
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
                      "template": "../maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
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
                    "template": "../maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                      "template": "../maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                        "url": "../maasto/wmts?"
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
                        "url": "../maasto/wmts?"
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
                        "url": "../maasto/wmts?"
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
            "wmsUrl": "../maasto/wmts",
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
                    "template": "../maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                      "template": "../maasto/wmts/1.0.0/taustakartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                    "template": "../maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
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
                      "template": "../maasto/wmts/1.0.0/ortokuva/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.jpg",
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
                    "template": "../maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                      "template": "../maasto/wmts/1.0.0/maastokartta/default/{TileMatrixSet}/{TileMatrix}/{TileRow}/{TileCol}.png",
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
                        "url": "../maasto/wmts?"
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
                        "url": "../maasto/wmts?"
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
                        "url": "../maasto/wmts?"
                      }]
                    }
                  }
                }
              },
              "version": "1.0.0"
            },
            "inspire": "Maastokartat"
          }],
            "imageLocation": "./bower_components/oskari.org/resources",
            "user": {
            "lastName": "",
              "nickName": "",
              "userUUID": "",
              "firstName": "",
              "loginName": ""
          }
        }
      },
      "openlayers-default-theme": {
        "state": {

        },
        "conf": {

        }
      },
      "divmanazer": {
        "state": {

        },
        "conf": {

        }
      },
      "statehandler": {
        "state": {

        },
        "conf": {

        }
      },
      "coordinatedisplay": {
        "state": {

        },
        "conf" : {
          "location" : {
            "classes" : "left bottom"
          }
        }
      }
    };
    }
  }
}(this));
