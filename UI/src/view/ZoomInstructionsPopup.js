(function (root) {
  root.ZoomInstructionsPopup = function(container) {
    var element =
      '<div class="zoom-instructions-popup">' +
        '<h3 class="popupHeader"/>' +
      '</div>';
    container.append(element);

    var show = function(message, timeout) {
      container.find('.popupHeader').text(message);
      container.find('.zoom-instructions-popup').fadeIn(200);
      setTimeout(function() { container.find('.zoom-instructions-popup').fadeOut(200); }, timeout);
    };

    container.find('.zoom-instructions-popup').fadeOut(200);

    return {
      show: show
    };
  };
})(this);