(function(root) {
  root.NavigationPanel = {
    initialize: initialize
  };

  function initialize(container, instructionsPopup, locationSearch) {
    container.append('<div class="navigation-panel"></div>');

    var element = $('.navigation-panel');

    element.append(new SearchBox(instructionsPopup, locationSearch).element);
  }
})(this);
