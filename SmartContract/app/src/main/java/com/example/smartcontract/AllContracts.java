package com.example.smartcontract;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smartcontract.mapUsers.MapActivity;
import com.example.smartcontract.models.ContractModel;
import com.example.smartcontract.models.ObjectModel;
import com.example.smartcontract.viewModel.AllContractsViewModel;
import com.example.smartcontract.viewModel.ProductLotViewModel;
import com.google.zxing.integration.android.IntentIntegrator;
import com.shreyaspatil.MaterialDialog.MaterialDialog;
import com.shreyaspatil.MaterialDialog.interfaces.DialogInterface;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AllContracts extends AppCompatActivity {

    RecyclerView rV;
    AllContractsAdapter adapter;
    Button button;
    List<ContractModel> mList;
    ProgressBar loader;
    AllContractsViewModel allContractsViewModel;
    TaskRunner taskRunner;

    IntentIntegrator qrScanLotId, qrScanProductId;
    EditText productId, lotId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_contracts);
        taskRunner = new TaskRunner();
        rV = findViewById(R.id.rV);
        button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog();
            }
        });
        loader = findViewById(R.id.loader);
        mList = new ArrayList<>();
        adapter = new AllContractsAdapter(this, mList);
        rV.setLayoutManager(new LinearLayoutManager(this));
        rV.setAdapter(adapter);
        allContractsViewModel = new AllContractsViewModel();
        fetchData();
    }

    void showDialog() {
        try {
            final Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.activity_add_contract);
            EditText address = dialog.findViewById(R.id.contractAddress);
            EditText name = dialog.findViewById(R.id.name);
            Button b = dialog.findViewById(R.id.button);
            ProgressBar loader = dialog.findViewById(R.id.loader);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (address.getText().toString().trim().isEmpty()) {
                        address.setError("Enter the contract address!");
                        return;
                    }
                    if (name.getText().toString().trim().isEmpty()) {
                        name.setError("Enter a suitable name!");
                        return;
                    }
                    for (ContractModel contractModel : mList) {
                        if (contractModel.getAddress().equals(address.getText().toString().trim())) {
                            Toast.makeText(AllContracts.this, "Following Supply-Chain already exists!", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    b.setVisibility(View.GONE);
                    loader.setVisibility(View.VISIBLE);
                    taskRunner.executeAsync(new checkIsUser(address.getText().toString().trim()), (result) -> {
                        if (result.isStatus()) {
                            if (!result.getData().isEmpty() && ((boolean)result.getData().get(0).getValue())) {
                                allContractsViewModel.addContract(address.getText().toString().trim(), name.getText().toString().trim())
                                        .observe(AllContracts.this, new Observer<ObjectModel>() {
                                            @Override
                                            public void onChanged(ObjectModel objectModel) {
                                                if (objectModel.isStatus()) {
                                                    mList.add(0, (ContractModel) objectModel.getObj());
                                                    adapter.notifyDataSetChanged();
                                                    dialog.dismiss();
                                                } else {
                                                    Toast.makeText(AllContracts.this, objectModel.getMessage(), Toast.LENGTH_SHORT).show();
                                                }
                                                loader.setVisibility(View.GONE);
                                                b.setVisibility(View.VISIBLE);
                                            }
                                        });
                            } else {
                                dialog.dismiss();
                                Toast.makeText(AllContracts.this, "Ask someone to add you because you don't have access to this Smart-Contract! ", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(AllContracts.this, result.getErrorMsg(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.show();
            dialog.getWindow().setAttributes(lp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void fetchData() {
        allContractsViewModel.getContracts().observe(this, new Observer<ObjectModel>() {
            @Override
            public void onChanged(ObjectModel objectModel) {
                if (objectModel.isStatus()) {
                    loader.setVisibility(View.GONE);
                    mList.addAll((Collection<? extends ContractModel>) objectModel.getObj());
                } else {
                    Toast.makeText(AllContracts.this, objectModel.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.toolbar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.logOut:
                showLogoutDialog();
                return true;
            case R.id.profile:
                showProfileDialog();
                return true;
            case R.id.scan:
                showBarcodeDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void showLogoutDialog() {
        MaterialDialog mDialog = new MaterialDialog.Builder(AllContracts.this)
                .setTitle("Confirmation")
                .setMessage("Are you sure you want to log out?")
                .setCancelable(true)
                .setPositiveButton("Confirm", new MaterialDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        dialogInterface.dismiss();
                        Intent intent = new Intent(AllContracts.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        clearSharedPref();
                        finish();
                    }
                })
                .setNegativeButton("Cancel", new MaterialDialog.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        dialogInterface.dismiss();
                    }
                })
                .build();
        mDialog.show();
    }

    void showProfileDialog() {
        try {
            final Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.profile_dialog);
            LinearLayout copyAddress = dialog.findViewById(R.id.copyAddress);
            TextView address = dialog.findViewById(R.id.publicAddress);
            TextView balance = dialog.findViewById(R.id.currentBalance);
            ProgressBar balLoader = dialog.findViewById(R.id.balLoader);
            address.setText(Data.publicKey);
            copyAddress.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("PublicAddress", address.getText().toString());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(AllContracts.this, "Public Address copied to clipboard!", Toast.LENGTH_SHORT).show();
                }
            });
            copyAddress.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("PublicAddress", address.getText().toString());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(AllContracts.this, "Public Address copied to clipboard!", Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
            taskRunner.executeAsync(new getAccBal(), (result) -> {
                balance.setText(result);
                balLoader.setVisibility(View.GONE);
                balance.setVisibility(View.VISIBLE);
            });
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.show();
            dialog.getWindow().setAttributes(lp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void showBarcodeDialog() {
        try {
            final Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.scan_dialog);
            ImageView scanProductId = dialog.findViewById(R.id.scanProductId);
            ImageView scanLotId = dialog.findViewById(R.id.scanLotId);
            productId = dialog.findViewById(R.id.productId);
            lotId = dialog.findViewById(R.id.lotId);
            Button track = dialog.findViewById(R.id.button);
            ProgressBar trackLoader = dialog.findViewById(R.id.trackLoader);
            qrScanProductId = new IntentIntegrator(this).setRequestCode(8);
            qrScanLotId = new IntentIntegrator(this).setRequestCode(9);
            ProductLotViewModel productLotViewModel = new ProductLotViewModel();
            scanProductId.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    qrScanProductId.initiateScan();
                }
            });
            scanLotId.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    qrScanLotId.initiateScan();
                }
            });
            track.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    productId.setError(null);
                    lotId.setError(null);
                    if (productId.getText().toString().trim().isEmpty() && lotId.getText().toString().trim().isEmpty()) {
                        productId.setError("Mandatory Field!");
                    } else if (!productId.getText().toString().trim().isEmpty()) {
                        track.setVisibility(View.GONE);
                        trackLoader.setVisibility(View.VISIBLE);
                        productLotViewModel.getAddress(productId.getText().toString().trim()).observe(AllContracts.this, new Observer<ObjectModel>() {
                            @Override
                            public void onChanged(ObjectModel objectModel) {
                                if (objectModel.isStatus()) {
                                    String contractAddress = (String) objectModel.getObj();
                                    Intent intent = new Intent(AllContracts.this, MapActivity.class);
                                    intent.putExtra("contractAddress", contractAddress);
                                    intent.putExtra("productId", productId.getText().toString().trim());
                                    intent.putExtra("isPermitted", true);
                                    startActivity(intent);
                                } else {
                                    Toast.makeText(AllContracts.this, objectModel.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                                trackLoader.setVisibility(View.GONE);
                                track.setVisibility(View.VISIBLE);
                            }
                        });
                    } else if (!lotId.getText().toString().trim().isEmpty()) {
                        trackLoader.setVisibility(View.VISIBLE);
                        productLotViewModel.getAddress(lotId.getText().toString().trim()).observe(AllContracts.this, new Observer<ObjectModel>() {
                            @Override
                            public void onChanged(ObjectModel objectModel) {
                                if (objectModel.isStatus()) {
                                    String contractAddress = (String) objectModel.getObj();
                                    Intent intent = new Intent(AllContracts.this, MapActivity.class);
                                    intent.putExtra("contractAddress", contractAddress);
                                    intent.putExtra("lotId", lotId.getText().toString().trim());
                                    intent.putExtra("isPermitted", true);
                                    startActivity(intent);

                                } else {
                                    Toast.makeText(AllContracts.this, objectModel.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                                trackLoader.setVisibility(View.GONE);
                                track.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }
            });
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
            lp.copyFrom(dialog.getWindow().getAttributes());
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            dialog.show();
            dialog.getWindow().setAttributes(lp);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class TaskRunner {
        //        private final Executor executor = Executors.newSingleThreadExecutor(); // change according to your requirements
        private final Executor executor = new ThreadPoolExecutor(5, 128, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        private final Handler handler = new Handler(Looper.getMainLooper());

        public interface Callback<R> {
            void onComplete(R result);
        }

        public <R> void executeAsync(Callable<R> callable, Dashboard.TaskRunner.Callback<R> callback) {
            executor.execute(() -> {
                try {
                    final R result = callable.call();
                    handler.post(() -> {
                        callback.onComplete(result);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    class checkIsUser implements Callable<Object> {
        String contractAddress;

        public checkIsUser(String contractAddress) {
            this.contractAddress = contractAddress;
        }

        @Override
        public Object call() {
            Object result = new Object();
            try {
                // Connect to the node
                System.out.println("Connecting to Ethereum ...");
                Web3j web3j = Web3j.build(new HttpService("https://rinkeby.infura.io/v3/55697f31d7db4e0693f15732b7e10e08"));

                // Load an account
                String pk = Data.privateKey;
                Credentials credentials = Credentials.create(pk);

                // Contract and functions
                List<Type> inputAsync = new ArrayList<>();
                inputAsync.add(new Address(Data.publicKey));
                List<TypeReference<?>> outputAsync = new ArrayList<>();
                outputAsync.add(new TypeReference<Bool>() {
                });
                Function function = new Function("checkIsUser", // Function name
                        inputAsync,  // Function input parameters
                        outputAsync); // Function returned parameters
                Log.d("Address Output: ", outputAsync.size() + "");
                String encodedFunction = FunctionEncoder.encode(function);
                EthCall ethCall = web3j.ethCall(
                        Transaction.createEthCallTransaction(credentials.getAddress(), contractAddress, encodedFunction),
                        DefaultBlockParameterName.LATEST)
                        .sendAsync().get();
                if (!ethCall.isReverted()) {
                    result = new Object(true, FunctionReturnDecoder.decode(ethCall.getValue(), function.getOutputParameters()), null);
                } else {
                    result = new Object(false, null, ethCall.getRevertReason() != null ? ethCall.getRevertReason() : "Something went wrong!");
                }
            } catch (Exception e) {
                Toast.makeText(AllContracts.this, e.toString(), Toast.LENGTH_SHORT).show();
                Log.d("Address Error: ", e.toString());
                e.printStackTrace();
            }
            return result;
        }
    }

    class Object {
        private List<Type> data;
        private boolean status;

        public boolean isStatus() {
            return status;
        }

        public void setStatus(boolean status) {
            this.status = status;
        }

        public Object() {
        }

        public List<Type> getData() {
            return data;
        }

        public void setData(List<Type> data) {
            this.data = data;
        }

        public Object(boolean status, List<Type> data, String errorMsg) {
            this.data = data;
            this.status = status;
            this.errorMsg = errorMsg;
        }

        public String getErrorMsg() {
            return errorMsg;
        }

        public void setErrorMsg(String errorMsg) {
            this.errorMsg = errorMsg;
        }

        private String errorMsg;
    }

    class getAccBal implements Callable<String> {

        @Override
        public String call() {
            String result = "";
            try {
                // Connect to the node
                System.out.println("Connecting to Ethereum ...");
                Web3j web3j = Web3j.build(new HttpService("https://rinkeby.infura.io/v3/55697f31d7db4e0693f15732b7e10e08"));

                EthGetBalance balanceResult = web3j.ethGetBalance(Data.publicKey, DefaultBlockParameterName.LATEST).send();
                BigInteger wei = balanceResult.getBalance();
                result = wei.doubleValue() / 1e18 + " ETH";
            } catch (Exception e) {
                Log.d("Address Error: ", e.toString());
                e.printStackTrace();
            }
            return result;
        }
    }

    void clearSharedPref() {
        SharedPreferences preferences = getSharedPreferences(BuildConfig.APPLICATION_ID, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 8) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String contents = data.getStringExtra("SCAN_RESULT");
                productId.setText(contents);
                productId.setSelection(productId.getText().length());
            } else {
                Toast.makeText(this, "Result Not Found", Toast.LENGTH_LONG).show();
            }
        }
        if (requestCode == 9) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String contents = data.getStringExtra("SCAN_RESULT");
                lotId.setText(contents);
                lotId.setSelection(productId.getText().length());
            } else {
                Toast.makeText(this, "Result Not Found", Toast.LENGTH_LONG).show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

}