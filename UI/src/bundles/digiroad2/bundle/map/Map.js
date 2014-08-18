Oskari.clazz.define('Oskari.digiroad2.bundle.map.Map',
  function() {
    this.mapModule = null;
    this.pluginName = null;
    this._sandbox = null;
    this._map = null;
  }, {
    __name: 'Map',
    _layerType: 'map',
    getName: function() {
      return this.pluginName;
    },
    getMapModule: function() {
      return this.mapModule;
    },
    setMapModule: function(mapModule) {
      this.mapModule = mapModule;
      this.pluginName = mapModule.getName() + this.__name;
    },
    hasUI: function() {
      return false;
    },
    register: function() {
      this.getMapModule().setLayerPlugin('map', this);
    },
    unregister: function() {
      this.getMapModule().setLayerPlugin('map', null);
    },
    init: function(sandbox) {
      new MapView(this._map, sandbox);

      // register domain builder
      var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
      if (mapLayerService) {
        mapLayerService.registerLayerModel('map', 'Oskari.digiroad2.bundle.map.domain.BusStopLayer');
      }
      sandbox.postRequestByName('RearrangeSelectedMapLayerRequest', ['base_35', 0]);
    },
    startPlugin: function(sandbox) {
      this._sandbox = sandbox;
      this._map = this.getMapModule().getMap();
      sandbox.register(this);
    },
    start: function(sandbox) {
    },
    preselectLayers: function(layers) {
      for (var i = 0; i < layers.length; i++) {
        var layer = layers[i];
        if (!layer.isLayerOfType(this._layerType)) {
          continue;
        }
        this._addMapLayerToMap(layer);
      }
    },
    _afterMapLayerAddEvent: function(event) {
      this._addMapLayerToMap(event.getMapLayer(), event.getKeepLayersOrder(), event.isBasemap());
    },
    getOLMapLayers: function(layer) {
      if (!layer.isLayerOfType(this._layerType)) {
        return null;
      }
      return [this.roadLayer];
    }
  }, {
    'protocol': ["Oskari.mapframework.module.Module", "Oskari.mapframework.ui.module.common.mapmodule.Plugin"]
  });
