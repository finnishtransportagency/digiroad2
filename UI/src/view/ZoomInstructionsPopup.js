(function (root) {
  root.ZoomInstructionsPopup = function(container) {
    var element =
      '<div class="zoom-instructions-popup">' +
        '<h3 class="popupHeader">Zoomaa lähemmäksi, jos haluat nähdä kohteita</h3>' +
      '</div>';
    container.append(element);

    var show = function(timeout) {
      container.find('.zoom-instructions-popup').fadeIn(200);
      setTimeout(function() { container.find('.zoom-instructions-popup').fadeOut(200); }, timeout);
    };

    container.find('.zoom-instructions-popup').fadeOut(200);

    return {
      show: show
    };
  };
})(this);