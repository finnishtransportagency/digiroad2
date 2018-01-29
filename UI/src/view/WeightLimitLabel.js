(function(root) {

  root.WeightLimitLabel = function(){
    LimitationLabel.call(this);
    var me = this;

    this.getImage = function (typeId) {
      var images = {
        320: 'images/blueLabeling.png'   ,
        330: 'images/greenLabeling.png',
        340: 'images/orangeLabeling.png',
        350: 'images/yellowLabeling.png'
      };
      return images[typeId];
    };
  };
})(this);