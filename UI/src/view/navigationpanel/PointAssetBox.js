(function(root) {
  root.PointAssetBox = function() {
    var title = 'Suojatie';
    var layerName = 'pedestrianCrossing';
    var className = _.kebabCase(layerName);
    var element = $('<div class="panel-group simple-limit ' + className + 's"></div>').hide();
    element.append('<div class="panel"><header class="panel-header expanded">Suojatie</header></div>');

    return {
      title: title,
      layerName: layerName,
      element: element,
      show: show,
      hide: hide
    };

    function show() {
      element.show();
    }

    function hide() {
      element.hide();
    }
  };
})(this);