(function (root) {
  root.LoadingBarDisplay = function (map, container) {
    var element = '<div class="loadingBar-container"></div>';
    container.append(element);
    var requests = 0;

    eventbus.on('loadingBar:show', function() {
      requests++;
      if (requests === 1) {
        setTimeout(function () {
          $('.loadingBar-container').append('<div class="loadingBar"></div>');
        }, 1000);
      }
    });

    eventbus.on('loadingBar:hide', function() {
      requests--;
      if (requests === 0) {
        setTimeout(function () {
          $('.loadingBar').remove();
        }, 1000);
      }
    });

  };
})(this);