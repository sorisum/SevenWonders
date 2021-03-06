package ca.polymtl.inf8405.sevenwonders;

import ca.polymtl.inf8405.sevenwonders.api.Player;
import ca.polymtl.inf8405.sevenwonders.api.Card;
import ca.polymtl.inf8405.sevenwonders.controller.CardLoader;
import ca.polymtl.inf8405.sevenwonders.controller.CivilisationLoader;
import ca.polymtl.inf8405.sevenwonders.database.Database;
import ca.polymtl.inf8405.sevenwonders.model.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.*;

public class PlayerStateView extends View{

	private HashMap<CardInfo, Bitmap> cardsInHand_;
	private List<CardInfo> listCardNames_ = new ArrayList<CardInfo>();
	private Player player_;
	private static float cardWidth_ = 0;
	private static float cardHeight_ = 0;
	private View self_ = this;

	private void init(Context context){
		// Image by default
		//		setBackgroundResource(R.drawable.seven_wonders_bg);
		cardsInHand_ = new HashMap<CardInfo, Bitmap>();
		setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent evt) {
				int selectedCardId = findSelectedCard(evt.getX(), evt.getY());
				if (selectedCardId != -1)
					GameScreenActivity.showZoomPopup(self_, selectedCardId, listCardNames_, true, player_.canPlayWonder);
				return false;
			}
		});
	}

	public PlayerStateView(Context context) {
		super(context);
		init(context);
	}

	public PlayerStateView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	@Override
	public void onDraw(Canvas canvas) {
		invalidate();

		// Calcul top, left
		int top = getHeight() / 6; // 40
		int left = 0;

		for (CardInfo card: listCardNames_){
			canvas.drawBitmap(cardsInHand_.get(card), left, top, null);
			left += (int)cardWidth_;
		}
	}

	public void setCardSize(float viewHeight){
		Bitmap cardSample = CardLoader.getInstance().getBitmap(getContext(), Card.TAVERN); // Fixme: ???
		cardHeight_ = viewHeight * 2 / 5;
		cardWidth_ = cardHeight_ * cardSample.getWidth() / cardSample.getHeight();
	}

	public void setCards(List<CardInfo> cards){
		listCardNames_ = cards;
		Bitmap cardBm;
		cardsInHand_.clear();
		for (CardInfo card: cards){
			if ((cardHeight_ == 0) && (cardWidth_ == 0)){
				cardBm = CardLoader.getInstance().getBitmap(getContext(), card.getName());
			}
			else{
				// Resize Bitmap
				cardBm = Bitmap.createScaledBitmap(
						CardLoader.getInstance().getBitmap(getContext(), card.getName()),
						(int)cardWidth_, (int)cardHeight_, false);
			}
			cardsInHand_.put(card, cardBm );
		}
	}

	public void setPlayer(Player player){
		invalidate();
		player_ = player;
		//		Drawable civiDrawable = CivilisationLoader.getInstance().getDrawable(getContext(), player_.civilisation);
		//		Bitmap resized = Bitmap.createScaledBitmap(CardLoader.drawableToBitmap(civiDrawable), 
		//				getWidth(), getHeight(), false);
		//		setBackgroundDrawable(new BitmapDrawable(resized));

		setBackgroundDrawable(CivilisationLoader.getInstance().getDrawable(getContext(), player_.civilisation));
	}


	private int findSelectedCard(float x, float y){
		for(int i = 0 ; i < cardsInHand_.size(); i++){
			if ( (i*cardWidth_ < x) && (x < (i+1)*cardWidth_))
				return i;
		}
		return -1;
	}
}
