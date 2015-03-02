(function(root) {
  root.Layer = function(layerName) {
    var me = this;
    this.eventListener = _.extend({running: false}, eventbus);
    this.refreshView = function() {};
    this.isDirty = function() { return false; };
    this.bindEventHandlers = function() {};
    this.removeLayerFeatures = function() {};
    this.isStarted = function() {
      return me.eventListener.running;
    };
    this.start = function() {
      if (!me.isStarted()) {
        me.selectControl.activate();
        me.eventListener.running = true;
        me.refreshView();
        me.bindEventHandlers(me.eventListener);
      }
    };
    this.stop = function() {
      if (me.isStarted()) {
        me.removeLayerFeatures();
        me.selectControl.deactivate();
        me.eventListener.stopListening(eventbus);
        me.eventListener.running = false;
      }
    };
    this.displayConfirmMessage = function() { new Confirm(); };
    this.handleMapMoved = function(state) {
      if (zoomlevels.isInRoadLinkZoomLevel(state.zoom) && state.selectedLayer === layerName) {
        if (!me.isStarted()) {
          me.start();
        }
        else {
          me.refreshView();
        }
      } else {
        me.stop();
      }
    };

    eventbus.on('map:moved', this.handleMapMoved);
  };
})(this);