(function (root) {
  root.LoadingBarDisplay = function (map, container) {
    var element = '<div class="loadingBar-container"></div>';
    container.append(element);

    eventbus.on('requests:applied', function() {
      $('.loadingBar-container').append('<div class="loadingBar"></div>');
    });

    eventbus.on('requests:ended', function() {
      $('.loadingBar').remove();
    });

  };
})(this);