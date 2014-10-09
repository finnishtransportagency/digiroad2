(function (root) {
  root.InstructionsPopup = function(container) {
    var element =
      '<div class="instructions-popup">' +
        '<h3 class="popupHeader"/>' +
      '</div>';
    container.append(element);

    var show = function(message, timeout) {
      container.find('.popupHeader').text(message);
      container.find('.instructions-popup').fadeIn(200);
      setTimeout(function() { container.find('.instructions-popup').fadeOut(200); }, timeout);
    };

    container.find('.instructions-popup').fadeOut(200);

    return {
      show: show
    };
  };
})(this);