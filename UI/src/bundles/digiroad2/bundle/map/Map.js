Oskari.clazz.define('Oskari.digiroad2.bundle.map.Map',
  function() {
    this.mapModule = null;
    this.pluginName = null;
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
      new MapView(this.mapModule.getMap(), sandbox);

      // register domain builder
      var mapLayerService = sandbox.getService('Oskari.mapframework.service.MapLayerService');
      if (mapLayerService) {
        mapLayerService.registerLayerModel('map', 'Oskari.digiroad2.bundle.map.domain.BusStopLayer');
      }
      sandbox.postRequestByName('RearrangeSelectedMapLayerRequest', ['base_35', 0]);
    },
    startPlugin: function(sandbox) {
      sandbox.register(this);
    },
    start: function(sandbox) {
    },
    getOLMapLayers: function(layer) {
    }
  }, {
    'protocol': ["Oskari.mapframework.module.Module", "Oskari.mapframework.ui.module.common.mapmodule.Plugin"]
  });
